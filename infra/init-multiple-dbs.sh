#!/bin/bash
# The official postgres image only creates ONE database from POSTGRES_DB.
# Since Service A and Service B intentionally own separate databases
# (physical isolation between services, not just separate schemas),
# this script creates both on container init, reading the
# POSTGRES_MULTIPLE_DATABASES env var (comma-separated) from docker-compose.yml.
set -e
set -u

function create_database() {
	local database=$1
	echo "Creating database '$database'"
	psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
	    CREATE DATABASE $database;
	    GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;
	EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
	echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
	for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
		create_database "$db"
	done
	echo "Multiple databases created"
fi
