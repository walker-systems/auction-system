from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Optional
from catboost import CatBoostClassifier
import pandas as pd

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

    data = pd.DataFrame([bid.model_dump() for bid in bids])

    expected_order = [
        "bid_amount", "ip_address", "user_agent",
        "reaction_time_ms", "bid_count_last_min", "is_new_ip"
    ]
    features = data[expected_order]

    probs = model.predict_proba(features)[:, 1]
    preds = model.predict(features)

    results = []
    for i in range(len(bids)):
        results.append(FraudCheckResponse(
            id=bids[i].id,
            fraud_probability=round(float(probs[i]), 4),
            is_fraud=bool(preds[i])
        ))

    return results

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
