#!/bin/bash

PGUSER=${POSTGRES_USER}
PGPASSWORD=${POSTGRES_PASSWORD}

## Store database admin password in Vault KV
vault secrets enable -version=2 kv
vault kv put kv/application ''
vault kv put kv/vault-static-secrets spring.datasource.username=${PGUSER} spring.datasource.password=${PGPASSWORD}

## Enable database secrets engine
vault secrets enable -path='database' database

vault write database/config/mydatabase \
	plugin_name=postgresql-database-plugin \
	connection_url='postgresql://{{username}}:{{password}}@postgres:5432/mydatabase' \
	allowed_roles="inventory-app" \
	username="${PGUSER}" \
	password="${PGPASSWORD}"

vault write database/roles/inventory-app \
	db_name=mydatabase \
	creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
     GRANT ALL PRIVILEGES ON DATABASE mydatabase TO \"{{name}}\"; \
     GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"{{name}}\"; \
     GRANT CREATE ON SCHEMA public TO \"{{name}}\"; \
     ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO \"{{name}}\";" \
	default_ttl="2m" \
	max_ttl="3m"



# Enable AppRole auth method (if not already enabled)
vault auth enable approle

# Create policy for inventory app
vault policy write inventory-policy -<<EOF

# Allow reading the KV secrets
path "secret/data/inventory/common" {
  capabilities = ["read"]
}

path "database/creds/inventory-app" {
  capabilities = ["read"]
}

path "kv/data/vault-static-secrets" {
  capabilities = ["read"]
}
EOF

## Create AppRole with custom role-id
#vault write auth/approle/role/inventory-app \
#    role_id="inventory-role-id" \
#    token_policies="inventory-policy" \
#    token_ttl=1h \
#    token_max_ttl=4h

# Create the path with some data
vault kv put secret/inventory/common \
    mykey=myvalue
# Create AppRole without requiring secret-id
vault write auth/approle/role/inventory-app \
    role_id="inventory-role-id" \
    bind_secret_id=false \
    token_policies="inventory-policy" \
    token_ttl=1h \
    token_max_ttl=4h