import pandas as pd
import random
from faker import Faker

fake = Faker()
Faker.seed(42)  
random.seed(42)

def generate_bids(num_bids, is_fraud):
    bids = []
    for _ in range(num_bids):
        user_name = fake.user_name()
        bid_amount = round(random.uniform(10.0, 5000.0), 2)
        ip_address = fake.ipv4()
        
        if is_fraud:
            # Fraudulent Behavior Profile: 
            # Bots usually have sub-50ms reaction times and weird/automated User-Agents
            user_agent = random.choice([
                "python-requests/2.31.0", 
                "curl/7.68.0", 
                "Go-http-client/1.1", 
                fake.user_agent() # Occasionally spoof a real browser
            ])
            reaction_time_ms = random.randint(1, 49) 
            
        else:
            # Legitimate Behavior Profile: 
            # Humans take time to react and use standard web browsers
            user_agent = fake.user_agent()
            reaction_time_ms = random.randint(250, 4500)

        bids.append({
            "user_name": user_name,
            "bid_amount": bid_amount,
            "ip_address": ip_address,
            "user_agent": user_agent,
            "reaction_time_ms": reaction_time_ms,
            "is_fraud": 1 if is_fraud else 0
        })
    return bids

if __name__ == "__main__":
    print("🤖 Generating 5,000 legitimate bids...")
    legit_bids = generate_bids(5000, is_fraud=False)
    
    print("🚨 Generating 500 fraudulent bot bids...")
    fraud_bids = generate_bids(500, is_fraud=True)
    
    # Combine and shuffle the dataset
    all_bids = legit_bids + fraud_bids
    random.shuffle(all_bids)
    
    # Export to CSV using Pandas
    df = pd.DataFrame(all_bids)
    output_filename = "fraud_dataset.csv"
    df.to_csv(output_filename, index=False)
    
    print(f"✅ Successfully exported {len(df)} records to {output_filename}")