#!/usr/bin/env python3
"""
Performance Test Data Generator for Event Ticket Booking Service
Generates users_tokens.csv with 50,000 valid signed JWT tokens.
No external dependencies required (uses standard Python library: hmac, hashlib, base64, json, time).
"""

import os
import sys
import json
import time
import base64
import hmac
import hashlib

# Default Secret Key matching .env and application.yaml
DEFAULT_SECRET_KEY = os.getenv(
    "JWT_SECRET_KEY",
    "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
)

TOTAL_USERS = int(os.getenv("TOTAL_USERS", "50000"))
USER_ID_OFFSET = int(os.getenv("USER_ID_OFFSET", "4")) # Assuming 1-3 are sample users
OUTPUT_CSV = os.path.join(os.path.dirname(__file__), "users_tokens.csv")

def base64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode('utf-8').rstrip('=')

def create_jwt_token(user_id: int, email: str, role: str, secret_key: str, expiration_days: int = 30) -> str:
    header = {
        "alg": "HS256",
        "typ": "JWT"
    }
    
    now_sec = int(time.time())
    exp_sec = now_sec + (expiration_days * 24 * 60 * 60)
    
    payload = {
        "sub": email,
        "role": role,
        "id": user_id,
        "iat": now_sec,
        "exp": exp_sec
    }
    
    header_b64 = base64url_encode(json.dumps(header, separators=(',', ':')).encode('utf-8'))
    payload_b64 = base64url_encode(json.dumps(payload, separators=(',', ':')).encode('utf-8'))
    
    signing_input = f"{header_b64}.{payload_b64}".encode('utf-8')
    key_bytes = secret_key.encode('utf-8')
    
    signature = hmac.new(key_bytes, signing_input, hashlib.sha256).digest()
    sig_b64 = base64url_encode(signature)
    
    return f"{header_b64}.{payload_b64}.{sig_b64}"

def main():
    print(f"=== Generating {TOTAL_USERS:,} Test User Tokens ===")
    print(f"Secret Key: {DEFAULT_SECRET_KEY[:10]}...{DEFAULT_SECRET_KEY[-10:]}")
    print(f"Output File: {OUTPUT_CSV}")
    
    start_time = time.time()
    
    # Write CSV
    with open(OUTPUT_CSV, "w", encoding="utf-8", newline="\n") as f:
        # Header
        f.write("user_id,email,token\n")
        
        for i in range(1, TOTAL_USERS + 1):
            user_id = USER_ID_OFFSET + i - 1
            email = f"perf_user_{i}@perf.com"
            role = "CUSTOMER"
            token = create_jwt_token(user_id, email, role, DEFAULT_SECRET_KEY)
            f.write(f"{user_id},{email},{token}\n")
            
            if i % 10000 == 0:
                print(f"Progress: {i:,} / {TOTAL_USERS:,} tokens generated...")
                
    elapsed = time.time() - start_time
    file_size_mb = os.path.getsize(OUTPUT_CSV) / (1024 * 1024)
    print(f"[OK] Finished generating {TOTAL_USERS:,} tokens in {elapsed:.2f}s!")
    print(f"File size: {file_size_mb:.2f} MB saved to {OUTPUT_CSV}")

if __name__ == "__main__":
    main()

