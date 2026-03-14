import json
import random
from faker import Faker

fake = Faker()
Faker.seed(99)
random.seed(99)

def generate_bot_swarm(num_bots):
    bots = []
    for _ in range(num_bots):
        is_malicious = random.random() < 0.10

        bots.append({
            "bidderId": fake.user_name() + str(random.randint(1, 999)),
            "ipAddress": fake.ipv4(),
            "userAgent": "python-requests/2.31.0" if is_malicious else fake.user_agent(),
            "baseReactionTimeMs": random.randint(10, 80) if is_malicious else random.randint(400, 2000),
            "isMalicious": is_malicious
        })
    return bots

if __name__ == "__main__":
    swarm = generate_bot_swarm(10000)
    with open("../bidding-engine/src/main/resources/bot_swarm.json", "w") as f:
        json.dump(swarm, f, indent=2)
    print(f"🤖 Successfully generated {len(swarm)} unique bots in bot_swarm.json!")
