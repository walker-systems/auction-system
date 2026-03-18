from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_predict_fraud_batch():
    payload = [
        {
            "id": "human-bid-1",
            "ip_address": "192.168.1.1",
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "reaction_time_ms": 1500,
            "bid_count_last_min": 2,
            "is_new_ip": 0,
            "bid_amount": 250.00
        },
        {
            "id": "bot-bid-1",
            "ip_address": "10.0.0.1",
            "user_agent": "python-requests/2.31.0",
            "reaction_time_ms": 12,
            "bid_count_last_min": 85,
            "is_new_ip": 1,
            "bid_amount": 255.00
        }
    ]

    response = client.post("/predict", json=payload)
    assert response.status_code == 200

    results = response.json()
    assert len(results) == 2

    # Prove the human passed
    human_result = next(r for r in results if r["id"] == "human-bid-1")
    assert human_result["is_fraud"] is False

    # Prove the bot failed
    bot_result = next(r for r in results if r["id"] == "bot-bid-1")
    assert bot_result["is_fraud"] is True
