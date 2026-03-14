import json
import random

tickers = [
    "AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "META", "GOOGL", "JPM", "GS", "BAC", "V", "MA",
    "SPY", "QQQ", "IWM", "VIX", "BTC", "ETH", "SOL", "XRP", "ADA", "AVAX", "LINK", "DOGE",
    "EUR/USD", "GBP/USD", "USD/JPY", "AUD/USD", "USD/CHF", "GOLD", "SILVER", "PLATINUM", "COPPER",
    "WTI", "BRENT", "NATGAS", "CORN", "SOYBEAN", "WHEAT", "US10Y", "US2Y", "US30Y", "BUND",
    "JGB", "GILT", "OIS", "CDX", "MBS", "LIBOR", "NFLX", "DIS", "UBER", "INTC", "AMD",
    "CRM", "NOW", "SNOW", "PLTR", "XOM", "CVX", "OXY", "LLY", "NVO", "JNJ", "UNH", "WMT",
    "COST", "HD", "MCD", "PEP", "KO", "SBUX", "TGT", "BA", "CAT", "LMT", "DE", "MMM"
]

types = ["BLK", "DARK", "C", "P", "FUT", "PERP", "SPOT", "SWAP", "FWD", "OPT"]
sizes = ["10K", "25K", "50K", "100K", "250K", "1M", "5M"]
months = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "0DTE", "1DTE"]

def generate_catalog(num_items):
    catalog = []
    seen_names = set()

    while len(catalog) < num_items:
        ticker = random.choice(tickers)
        instr = random.choice(types)

        if instr in ["C", "P", "OPT"]:
            strike = random.randint(10, 500) * 5
            suffix = random.choice(months)
            name = f"{ticker}-{instr}{strike}-{suffix}"
        elif instr in ["FUT", "FWD"]:
            suffix = random.choice(months)
            name = f"{ticker}-{instr}-{suffix}"
        else:
            suffix = random.choice(sizes)
            name = f"{ticker}-{instr}-{suffix}"

        if name not in seen_names:
            seen_names.add(name)

            if instr in ["C", "P", "OPT"]:
                price = round(random.uniform(0.1, 85.0), 2)
            elif ticker == "BTC":
                price = round(random.uniform(62000.0, 71000.0), 2)
            elif ticker == "ETH":
                price = round(random.uniform(3000.0, 4000.0), 2)
            elif "USD" in ticker or ticker in ["EUR/GBP"]:
                price = round(random.uniform(0.5, 1.5), 4)
            elif ticker in ["GOLD", "PALLADIUM"]:
                price = round(random.uniform(1800.0, 2400.0), 2)
            elif ticker in ["SILVER", "COPPER", "WTI", "BRENT"]:
                price = round(random.uniform(20.0, 90.0), 2)
            else:
                price = round(random.uniform(10.0, 600.0), 2)

            catalog.append({
                "itemId": name,
                "startingPrice": price
            })

    return catalog

if __name__ == "__main__":
    catalog = generate_catalog(10000)
    with open("../bidding-engine/src/main/resources/auctions_catalog.json", "w") as f:
        json.dump(catalog, f, indent=2)
    print(f"✅ Generated {len(catalog)} financial instruments!")
