import xgboost as xgb
from sklearn.datasets import make_classification
from sklearn.model_selection import train_test_split
from xgboost.callback import TrainingCheckPoint
import os
import glob
import joblib

checkpoint_dir = "checkpoints"
checkpoint_prefix = os.path.join(checkpoint_dir, "xgb_checkpoint")
checkpoint_path = None
final_model_path = "trained_model.pkl"

os.makedirs(checkpoint_dir, exist_ok=True)

print("Generating large synthetic dataset...")
X, y = make_classification(
    n_samples=500_000,
    n_features=50,
    n_informative=30,
    n_classes=2,
    random_state=42
)

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2)

dtrain = xgb.DMatrix(X_train, label=y_train)
dtest = xgb.DMatrix(X_test, label=y_test)

print("Setting training parameters...")

params = {
    "objective": "binary:logistic",
    "eval_metric": "logloss",
    "max_depth": 10,
    "eta": 0.05,
    "subsample": 0.8,
    "colsample_bytree": 0.8,
}

checkpoint_files = glob.glob(f"{checkpoint_prefix}-*.model")
if checkpoint_files:
    checkpoint_files.sort(key=os.path.getmtime, reverse=True)
    checkpoint_path = checkpoint_files[0]
    print(f"Resuming training from checkpoint: {checkpoint_path}")
else:
    print("Starting fresh training...")

checkpoint_callback = TrainingCheckPoint(directory=checkpoint_dir, name='xgb_checkpoint', interval=10)

bst = xgb.train(
    params=params,
    dtrain=dtrain,
    num_boost_round=100,
    evals=[(dtest, "eval")],
    xgb_model=checkpoint_path,
    callbacks=[checkpoint_callback]
)

joblib.dump(bst, final_model_path)
print(f"Training complete. Model saved to {final_model_path}")
