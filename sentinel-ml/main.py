from fastapi import FastAPI
from pydantic import BaseModel
from catboost import CatBoostClassifier
import pandas as pd

app = FastAPI(title="Sentinel ML Service")

model = CatBoostClassifier()
model.load_model("fraud_model.cbm")

class BidRequest(BaseModel):
    ip_address: str
    user_agent: str
    reaction_time_ms: int
    bid_count_last_min: int
    is_new_ip: int
    bid_amount: float

@app.post("/predict")
def predict_fraud(bid: BidRequest):
    data = pd.DataFrame([bid.model_dump()])
    
    expected_order = [
        "bid_amount", "ip_address", "user_agent", 
        "reaction_time_ms", "bid_count_last_min", "is_new_ip"
    ]
    data = data[expected_order]
    
    prob = model.predict_proba(data)[0][1]
    is_fraud = bool(model.predict(data)[0])
    
    return {
        "fraud_probability": round(float(prob), 4),
        "is_fraud": is_fraud
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
