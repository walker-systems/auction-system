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
        
        is_new_ip = 1 if (is_fraud or random.random() < 0.2) else 0

        if is_fraud:
            if random.random() < 0.30:
                reaction_time_ms = random.randint(400, 1200) # Human speed
                bid_count_last_min = random.randint(1, 3)    # Human frequency
                user_agent = fake.user_agent()               
            else:
                reaction_time_ms = random.randint(10, 150)   # Fast bot
                bid_count_last_min = random.randint(10, 50)  # High frequency
                user_agent = "python-requests/2.31.0"
        else:
            if random.random() < 0.10:
                reaction_time_ms = random.randint(100, 200) # Fast "Sniper"
            else:
                reaction_time_ms = random.randint(300, 5000)
            
            bid_count_last_min = random.randint(1, 5)
            user_agent = fake.user_agent()

        bids.append({
            "user_name": user_name,
            "bid_amount": bid_amount,
            "ip_address": ip_address,
            "user_agent": user_agent,
            "reaction_time_ms": reaction_time_ms,
            "bid_count_last_min": bid_count_last_min, 
            "is_new_ip": is_new_ip,                   
            "is_fraud": 1 if is_fraud else 0
        })
    return bids

if __name__ == "__main__":
    print("🤖 Generating 5,000 legitimate bids...")
    legit_bids = generate_bids(5000, is_fraud=False)
    
    print("🚨 Generating 500 fraudulent bot bids...")
    fraud_bids = generate_bids(500, is_fraud=True)
    
    all_bids = legit_bids + fraud_bids
    random.shuffle(all_bids)
    
    df = pd.DataFrame(all_bids)
    output_filename = "fraud_dataset.csv"
    df.to_csv(output_filename, index=False)
    
    print(f"✅ Successfully exported {len(df)} records to {output_filename}")