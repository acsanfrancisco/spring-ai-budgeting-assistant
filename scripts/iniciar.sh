#!/usr/bin/env bash
set -eu
cd -- "$(dirname -- "$0")/.."
umask 077

# Runs on Git Bash (Windows), Linux and macOS. macOS ships bash 3.2, so avoid
# bash 4 features such as associative arrays and ${var,,}.

config=.env
keys=(DB_USER_KEY DB_PASSWORD_KEY SERVER_PORT_KEY JWT_KEY JWT_EXPIRATION
      AI_ENCRYPTION_PASSWORD AI_ENCRYPTION_SALT)
all_keys=("${keys[@]}")

# Each setting lives in its own variable, cfg_<KEY>. Clear them so values
# inherited from the environment never leak into the generated .env.
for key in "${all_keys[@]}"; do unset "cfg_$key"; done

get() {
    local name=cfg_$1
    printf '%s' "${!name-}"
}

set_value() {
    printf -v "cfg_$1" '%s' "$2"
}

# Preserve Compose quoting literally; never execute configuration as shell code.
read_settings() {
    local line key value
    while IFS= read -r line || [[ -n $line ]]; do
        line=${line%$'\r'}
        [[ $line =~ ^[[:space:]]*([A-Z_]+)=(.*)$ ]] || continue
        key=${BASH_REMATCH[1]}
        value=${BASH_REMATCH[2]}
        case " ${all_keys[*]} " in
            *" $key "*) set_value "$key" "$value" ;;
        esac
    done < "$1"
}

missing() {
    local name=cfg_$1
    local value=${!name-}
    [[ -z $value || $value == "''" || $value == '""' ]]
}

if ! docker info >/dev/null 2>&1; then
    case $(uname -s) in
        Linux)
            echo 'O Docker não está respondendo. Inicie o serviço (ex.: sudo systemctl start docker)' >&2
            echo 'ou o Docker Desktop, e confira se seu usuário pode usá-lo (grupo docker).' >&2
            ;;
        *)
            echo 'Abra o Docker Desktop e aguarde o mecanismo de containers iniciar.' >&2
            ;;
    esac
    exit 1
fi

if [[ -f $config ]]; then
    read_settings "$config"
fi

needs_setup=false
for key in "${keys[@]}"; do
    if missing "$key"; then needs_setup=true; fi
done

if "$needs_setup"; then
    protect_volume=false
    for key in DB_USER_KEY DB_PASSWORD_KEY AI_ENCRYPTION_PASSWORD AI_ENCRYPTION_SALT; do
        if missing "$key"; then protect_volume=true; fi
    done
    if missing DB_USER_KEY; then
        read -r -p 'Usuário do PostgreSQL [budgeting]: ' username
        set_value DB_USER_KEY "${username:-budgeting}"
        if [[ ! $(get DB_USER_KEY) =~ ^[a-zA-Z_][a-zA-Z0-9_]{0,62}$ ]]; then
            echo 'Use letras, números e sublinhado; comece com letra ou sublinhado (máximo 63 caracteres).' >&2
            exit 1
        fi
    fi
    if missing DB_PASSWORD_KEY; then
        IFS= read -r -s -p 'Senha do PostgreSQL [Enter para gerar]: ' password
        printf '\n'
        if [[ -n $password ]]; then
            # Compose double quotes support escapes; $$ preserves a literal dollar.
            password=${password//\\/\\\\}
            password=${password//\"/\\\"}
            password=${password//\$/\$\$}
            set_value DB_PASSWORD_KEY "\"$password\""
        fi
    fi
    if missing SERVER_PORT_KEY; then
        read -r -p 'Porta local da API [8080]: ' port
        port=${port:-8080}
        if [[ ! $port =~ ^[1-9][0-9]{0,4}$ ]] || (( port > 65535 )); then
            echo 'Informe uma porta entre 1 e 65535.' >&2
            exit 1
        fi
        set_value SERVER_PORT_KEY "$port"
    fi
    for key in DB_PASSWORD_KEY JWT_KEY AI_ENCRYPTION_PASSWORD AI_ENCRYPTION_SALT; do
        if missing "$key"; then set_value "$key" "$(openssl rand -hex 32)"; fi
    done
    if missing JWT_EXPIRATION; then set_value JWT_EXPIRATION 86400000; fi
fi

if "$needs_setup"; then
    temp=$(mktemp .budgeting-env.XXXXXXXX)
    trap 'rm -f -- "$temp"' EXIT
    # New installations inherit the template's comments and layout. When repairing
    # a partial file, keep local comments and settings in their original positions.
    source_file=.env.example
    if [[ -f $config ]]; then
        if [[ -s $config ]]; then source_file=$config; fi
    fi
    written=' '
    while IFS= read -r line || [[ -n $line ]]; do
        line=${line%$'\r'}
        if [[ $line =~ ^[[:space:]]*([A-Z_]+)= ]]; then
            key=${BASH_REMATCH[1]}
            if [[ " ${all_keys[*]} " == *" $key "* ]]; then
                line="$key=$(get "$key")"
                written+="$key "
            fi
        fi
        printf '%s\n' "$line"
    done < "$source_file" > "$temp"
    for key in "${all_keys[@]}"; do
        if [[ $written != *" $key "* ]]; then printf '%s=%s\n' "$key" "$(get "$key")" >> "$temp"; fi
    done
    docker compose --env-file "$temp" config --quiet
    if ${protect_volume:-false}; then
        project=$(docker compose --env-file "$temp" config --format json |
            sed -n 's/^  *"name": "\([^"]*\)",$/\1/p' | head -1)
        if [[ -z $project ]]; then
            echo 'Não foi possível identificar o projeto Compose.' >&2
            exit 1
        fi
        volumes=$(docker volume ls -q --filter "label=com.docker.compose.project=$project" \
            --filter 'label=com.docker.compose.volume=postgres_data')
        if [[ -n $volumes ]]; then
            # docker compose down -v would fail here: Compose needs the missing credentials to load.
            containers=$(docker ps -a --filter "volume=$volumes" --format '{{.Names}}' | tr '\n' ' ')
            {
                printf '\nJá existe um banco de dados de uma instalação anterior (volume %s).\n' "$volumes"
                printf 'Ele só aceita as credenciais com que foi criado, e elas não estão no .env atual.\n\n'
                printf 'Para reaproveitar os dados: restaure o .env da instalação anterior e rode o script de novo.\n\n'
                printf 'Para começar do zero, APAGANDO os dados anteriores, rode:\n'
                if [[ -n ${containers// /} ]]; then
                    printf '  docker rm -f %s\n' "$containers"
                fi
                printf '  docker volume rm %s\n' "$volumes"
                printf 'e depois execute o script de novo.\n'
            } >&2
            exit 1
        fi
    fi
    mv -- "$temp" "$config"
fi

docker compose up --build -d
printf 'Swagger (após a inicialização): http://localhost:%s/swagger-ui/index.html\n' "$(get SERVER_PORT_KEY)"
