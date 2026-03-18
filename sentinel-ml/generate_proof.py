import pandas as pd
from catboost import CatBoostClassifier
import shap
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.metrics import confusion_matrix

model = CatBoostClassifier()
model.load_model("fraud_model.cbm")
df = pd.read_csv("fraud_dataset.csv")

features = ["bid_amount", "ip_address", "user_agent", "reaction_time_ms", "bid_count_last_min", "is_new_ip"]
X = df[features]
y_true = df["is_fraud"]
y_pred = model.predict(X)

cm = confusion_matrix(y_true, y_pred)
plt.figure(figsize=(6, 5))
sns.heatmap(cm, annot=True, fmt="d", cmap="Blues", xticklabels=["Human", "Bot"], yticklabels=["Human", "Bot"])
plt.title("Fraud Detection Confusion Matrix")
plt.ylabel("Actual Origin")
plt.xlabel("Predicted Origin")
plt.savefig("confusion_matrix.png", bbox_inches='tight')
print("✅ Saved confusion_matrix.png")

explainer = shap.TreeExplainer(model)
shap_values = explainer(X)

plt.figure()
bot_index = df[df['is_fraud'] == 1].index[0]
shap.plots.waterfall(shap_values[bot_index], show=False)
plt.title(f"SHAP Analysis: Why Bid #{bot_index} was flagged as Fraud")
plt.savefig("shap_waterfall.png", bbox_inches='tight')
print("✅ Saved shap_waterfall.png")
