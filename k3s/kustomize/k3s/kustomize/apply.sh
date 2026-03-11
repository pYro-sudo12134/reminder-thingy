#!/bin/bash

ENV=${1:-dev}
NAMESPACE=${2:-app}

echo "Applying configuration for environment: $ENV"

if [ -f "overlays/$ENV/.env.$ENV" ]; then
    export $(grep -v '^#' overlays/$ENV/.env.$ENV | xargs)
fi

kubectl apply -k overlays/$ENV

if [ $? -eq 0 ]; then
    echo "Successfully applied $ENV configuration"

    echo ""
    echo "Created secrets:"
    kubectl get secrets -n $NAMESPACE | grep -E 'secrets|tls|certs'
else
    echo "Failed to apply configuration"
    exit 1
fi
