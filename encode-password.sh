#!/bin/bash

source .env

urlencode() {
    local string="${1}"
    local strlen=${#string}
    local encoded=""
    local pos c o

    for (( pos=0 ; pos<strlen ; pos++ )); do
        c=${string:$pos:1}
        case "$c" in
            [-_.~a-zA-Z0-9] ) o="${c}" ;;
            * )               printf -v o '%%%02x' "'$c"
        esac
        encoded+="${o}"
    done
    echo "${encoded}"
}

export POSTGRES_PASSWORD_URLENCODED=$(urlencode "$POSTGRES_PASSWORD")
export REDIS_PASSWORD_URLENCODED=$(urlencode "$REDIS_PASSWORD")

grep -q "POSTGRES_PASSWORD_URLENCODED" .env || echo "POSTGRES_PASSWORD_URLENCODED=$POSTGRES_PASSWORD_URLENCODED" >> .env
grep -q "REDIS_PASSWORD_URLENCODED" .env || echo "REDIS_PASSWORD_URLENCODED=$REDIS_PASSWORD_URLENCODED" >> .env