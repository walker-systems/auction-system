from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Optional
from catboost import CatBoostClassifier

app = FastAPI(title="Sentinel ML Service")

model = CatBoostClassifier()
model.load_model("fraud_model.cbm")

class BidRequest(BaseModel):
    id: str
    ip_address: Optional[str] = "unknown"
    user_agent: Optional[str] = "unknown"
    reaction_time_ms: int
    bid_count_last_min: int
    is_new_ip: int
    bid_amount: float

class FraudCheckResponse(BaseModel):
    id: str
    fraud_probability: float
    is_fraud: bool

@app.post("/predict", response_model=List[FraudCheckResponse])
def predict_fraud_batch(bids: List[BidRequest]):
    if not bids:
        return []

    features = [
        [
            bid.bid_amount,
            bid.ip_address,
            bid.user_agent,
            bid.reaction_time_ms,
            bid.bid_count_last_min,
            bid.is_new_ip
        ]
        for bid in bids
    ]

    probs = model.predict_proba(features)[:, 1]
    predictions = model.predict(features)

    results = []
    for i in range(len(bids)):
        results.append(FraudCheckResponse(
            id=bids[i].id,
            fraud_probability=round(float(probs[i]), 4),
            is_fraud=bool(predictions[i])
        ))

    return results
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
