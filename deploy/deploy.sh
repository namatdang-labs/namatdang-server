#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: deploy.sh <40-character-image-tag> <release-directory>" >&2
    exit 2
fi

image_tag="$1"
release_dir="$2"
app_dir="/opt/namatdang/app"
compose_file="$app_dir/compose.prod.yml"
release_env="$app_dir/release.env"
runtime_env="$app_dir/runtime.env"
image_archive="$release_dir/namatdang-api.tar.gz"
image_checksum="$release_dir/namatdang-api.tar.gz.sha256"

if [[ ! "$image_tag" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Invalid image tag: expected a full Git commit SHA." >&2
    exit 2
fi

case "$release_dir" in
    /opt/namatdang/releases/*) ;;
    *)
        echo "Invalid release directory." >&2
        exit 2
        ;;
esac

required_files=(
    "$image_archive"
    "$image_checksum"
    "$release_dir/compose.prod.yml"
    "$runtime_env"
    /opt/namatdang/secrets/db-password
    /opt/namatdang/secrets/jwt-private.b64
    /opt/namatdang/secrets/jwt-public.b64
    /opt/namatdang/certs/rds-truststore.p12
)

for required_file in "${required_files[@]}"; do
    if [[ ! -s "$required_file" ]]; then
        echo "Required deployment file is missing or empty: $required_file" >&2
        exit 1
    fi
done

rollback_dir="$(mktemp -d "$app_dir/.rollback.XXXXXX")"
trap 'rm -rf "$rollback_dir"' EXIT

had_previous_release=false
if [[ -s "$compose_file" && -s "$release_env" ]]; then
    cp -p "$compose_file" "$rollback_dir/compose.prod.yml"
    cp -p "$release_env" "$rollback_dir/release.env"
    had_previous_release=true
fi

(
    cd "$release_dir"
    sha256sum --check "$(basename "$image_checksum")"
)

docker load --input "$image_archive" >/dev/null
docker image inspect "namatdang-api:$image_tag" >/dev/null
rm -f "$image_archive" "$image_checksum"

install -m 0644 "$release_dir/compose.prod.yml" "$compose_file"
release_env_temp="$(mktemp "$app_dir/release.env.XXXXXX")"
{
    echo "IMAGE_TAG=$image_tag"
    echo "RUNTIME_ENV_FILE=$runtime_env"
} > "$release_env_temp"
chmod 0600 "$release_env_temp"
mv "$release_env_temp" "$release_env"

compose() {
    docker compose \
        --project-name namatdang \
        --env-file "$release_env" \
        --file "$compose_file" \
        "$@"
}

rollback() {
    if [[ "$had_previous_release" == true ]]; then
        install -m 0644 "$rollback_dir/compose.prod.yml" "$compose_file"
        install -m 0600 "$rollback_dir/release.env" "$release_env"
        compose up --detach --no-build --remove-orphans
    else
        compose down
        rm -f "$compose_file" "$release_env"
    fi
}

if ! compose up --detach --no-build --remove-orphans; then
    echo "Docker Compose failed to start the new release. Restoring the previous release." >&2
    rollback
    exit 1
fi

deployment_ready=false
for _ in $(seq 1 30); do
    if docker inspect --format '{{.State.Running}}' namatdang-api 2>/dev/null \
        | grep -qx true \
        && curl --fail --silent --show-error \
            --output /dev/null \
            "http://127.0.0.1:8080/api/v1/stores?size=1"; then
        deployment_ready=true
        break
    fi
    sleep 3
done

if [[ "$deployment_ready" != true ]]; then
    echo "Deployment verification failed. Restoring the previous release." >&2
    compose logs --tail 300 app >&2 || true
    rollback
    exit 1
fi

echo "Deployment completed for image tag $image_tag."
