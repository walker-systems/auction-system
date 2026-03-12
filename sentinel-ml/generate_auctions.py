import json
import random

adjectives = [
    "Vintage", "Refurbished", "Brand New", "Limited Edition", "Signed",
    "Antique", "Factory Sealed", "Custom", "Mint Condition", "Rare",
    "Matte Black", "Rose Gold", "Stainless Steel", "Portable", "Wireless",
    "Smart", "Ergonomic", "Heavy Duty", "Minimalist", "Retro", "Tactical",
    "Professional", "Ultra-Slim", "Waterproof", "Open Box"
]

brands = [
    "Sony", "Apple", "NVIDIA", "Herman Miller", "La Marzocco",
    "Rolex", "Samsung", "LG", "Nike", "Tesla", "Omega", "Nintendo",
    "Canon", "Bose", "Dyson", "Logitech", "Razer", "Patagonia", "Yeti",
    "Breville", "Brembo", "Sennheiser", "Garmin", "Fender", "Leica"
]

nouns = [
    "Monitor", "Keyboard", "GPU", "Chair", "Espresso Machine",
    "Watch", "Smartphone", "Laptop", "Sneakers", "Hoverboard", "Camera",
    "Headphones", "Microphone", "Tablet", "Drone", "Backpack", "Desk",
    "Synthesizer", "Projector", "Telescope", "Router", "Amplifier",
    "Smartwatch", "Lens", "Console"
]

def generate_catalog(num_items):
    catalog = []
    seen_names = set()

    # 25 * 25 * 25 = 15,625 possible unique combinations
    while len(catalog) < num_items:
        name = f"{random.choice(adjectives)} {random.choice(brands)} {random.choice(nouns)}"

        if name not in seen_names:
            seen_names.add(name)
            # Generate a realistic starting price
            price = round(random.uniform(10.0, 800.0), 2)

            catalog.append({
                "itemId": name,
                "startingPrice": price
            })

    return catalog

if __name__ == "__main__":
    # Generate 10,000 unique items
    items = generate_catalog(10000)
    with open("../bidding-engine/src/main/resources/auctions_catalog.json", "w") as f:
        json.dump(items, f, indent=2)
    print(f"📦 Successfully generated {len(items)} unique auctions in auctions_catalog.json!")
