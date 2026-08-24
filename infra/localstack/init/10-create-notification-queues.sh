#!/bin/sh
set -eu

NAMATDANG_DLQ_NAME="namatdang-notification-events-dlq"
NAMATDANG_QUEUE_NAME="namatdang-notification-events"

awslocal sqs create-queue --queue-name "$NAMATDANG_DLQ_NAME" >/dev/null
NAMATDANG_DLQ_URL="$(awslocal sqs get-queue-url \
    --queue-name "$NAMATDANG_DLQ_NAME" \
    --query QueueUrl \
    --output text)"
NAMATDANG_DLQ_ARN="$(awslocal sqs get-queue-attributes \
    --queue-url "$NAMATDANG_DLQ_URL" \
    --attribute-names QueueArn \
    --query Attributes.QueueArn \
    --output text)"
NAMATDANG_REDRIVE_ATTRIBUTES="$(printf \
    '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' \
    "$NAMATDANG_DLQ_ARN")"

awslocal sqs create-queue --queue-name "$NAMATDANG_QUEUE_NAME" >/dev/null
NAMATDANG_QUEUE_URL="$(awslocal sqs get-queue-url \
    --queue-name "$NAMATDANG_QUEUE_NAME" \
    --query QueueUrl \
    --output text)"

awslocal sqs set-queue-attributes \
    --queue-url "$NAMATDANG_QUEUE_URL" \
    --attributes "$NAMATDANG_REDRIVE_ATTRIBUTES"
awslocal sqs set-queue-attributes \
    --queue-url "$NAMATDANG_QUEUE_URL" \
    --attributes '{"VisibilityTimeout":"5"}'
awslocal sqs set-queue-attributes \
    --queue-url "$NAMATDANG_QUEUE_URL" \
    --attributes '{"ReceiveMessageWaitTimeSeconds":"0"}'
