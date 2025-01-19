#!/bin/sh

PGPASSWORD=${POSTGRES_PASSWORD} psql -h postgres -U ${POSTGRES_USER} -f /schema.sql ${POSTGRES_DB}