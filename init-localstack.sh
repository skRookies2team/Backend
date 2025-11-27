#!/bin/bash

# LocalStack S3 버킷 생성 스크립트
echo "Waiting for LocalStack to be ready..."
sleep 5

echo "Creating S3 bucket: story-game-local"
aws --endpoint-url=http://localhost:4566 s3 mb s3://story-game-local --region ap-northeast-2

echo "Listing buckets..."
aws --endpoint-url=http://localhost:4566 s3 ls

echo "LocalStack S3 is ready!"
