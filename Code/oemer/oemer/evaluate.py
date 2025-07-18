import os
import json
import pickle
import numpy as np
import tensorflow as tf
import matplotlib.pyplot as plt
from tensorflow.keras.metrics import MeanIoU
import tensorflow_addons as tfa
from datetime import datetime
from tensorflow.keras import mixed_precision
mixed_precision.set_global_policy('mixed_float16')


from .train import (
    get_cvc_data_paths,
    get_deep_score_data_paths,
    DataLoader,
    DsDataLoader,
    WarmUpLearningRate,
    focal_tversky_loss,
    CHANNEL_NUM
)

def load_model_from_checkpoint(checkpoint_path):
    """Load a model from checkpoint directory"""
    # Load architecture
    with open(os.path.join(checkpoint_path, "arch.json"), "r") as f:
        model = tf.keras.models.model_from_json(f.read())
    
    # Load weights
    model.load_weights(os.path.join(checkpoint_path, "weights.h5"))
    
    # Load metadata if exists
    metadata_path = os.path.join(checkpoint_path, "metadata.pkl")
    metadata = {}
    if os.path.exists(metadata_path):
        with open(metadata_path, "rb") as f:
            metadata = pickle.load(f)
    
    return model, metadata

def evaluate_model(
    checkpoint_path,
    dataset_path,
    data_model="segnet",
    batch_size=8,
    steps=100,
    val_steps=50,
    epochs=10
):
    from matplotlib.ticker import MaxNLocator

    plots_dir = os.path.join(os.path.dirname(checkpoint_path), "plots")
    os.makedirs(plots_dir, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    model, metadata = load_model_from_checkpoint(checkpoint_path)
    input_shape = model.input_shape[1:3]
    win_size = input_shape[0]

    if data_model == "segnet":
        feat_files = get_deep_score_data_paths(dataset_path)
        DataLoaderClass = DsDataLoader
    else:
        feat_files = get_cvc_data_paths(dataset_path)
        DataLoaderClass = DataLoader

    all_pages = list(set(os.path.basename(x[0]) for x in feat_files))
    np.random.shuffle(all_pages)
    split_idx = round(0.1 * len(all_pages))
    val_page_names = set(all_pages[:split_idx])
    train_files = [f for f in feat_files if os.path.basename(f[0]) not in val_page_names]
    val_files = [f for f in feat_files if os.path.basename(f[0]) in val_page_names]

    train_data = DataLoaderClass(train_files, win_size=win_size, num_samples=steps * batch_size).get_dataset(batch_size)
    val_data = DataLoaderClass(val_files, win_size=win_size, num_samples=val_steps * batch_size).get_dataset(batch_size)

    def dice_coef(y_true, y_pred, smooth=1):
        y_true_f = tf.keras.backend.flatten(y_true)
        y_pred_f = tf.keras.backend.flatten(y_pred)
        intersection = tf.keras.backend.sum(y_true_f * y_pred_f)
        return (2. * intersection + smooth) / (tf.keras.backend.sum(y_true_f) + tf.keras.backend.sum(y_pred_f) + smooth)

    # Disable training updates
    model.trainable = False
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.0),  # No updates
        loss=focal_tversky_loss,
        metrics=["accuracy", dice_coef]
    )

    print(f"\nSimulating training over {epochs} epochs...")
    history = model.fit(
        train_data,
        validation_data=val_data,
        epochs=epochs,
        steps_per_epoch=steps,
        validation_steps=val_steps,
        shuffle=False,
        verbose=1
    )

    # Plot training history
    plt.figure(figsize=(12, 6))
    ax = plt.gca()
    ax.xaxis.set_major_locator(MaxNLocator(integer=True))

    for metric in ['loss', 'accuracy', 'dice_coef']:
        if metric in history.history:
            plt.plot(history.history[metric], label=f"train_{metric}")
        val_metric = f"val_{metric}"
        if val_metric in history.history:
            plt.plot(history.history[val_metric], label=val_metric)

    plt.title("Training History (Simulated)")
    plt.xlabel("Epoch")
    plt.ylabel("Metric Value")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(plots_dir, f"training_history_{timestamp}.png"))
    plt.close()

    # Get final epoch metrics
    final_train_metrics = {k: history.history[k][-1] for k in history.history if not k.startswith('val_')}
    final_val_metrics = {k[4:]: history.history[k][-1] for k in history.history if k.startswith('val_')}

    # Save metrics
    metrics_file = os.path.join(plots_dir, f"simulated_metrics_{timestamp}.txt")
    with open(metrics_file, "w") as f:
        f.write("Simulated Training Metrics (Last Epoch):\n")
        for metric, value in final_train_metrics.items():
            f.write(f"{metric}: {value:.4f}\n")
        f.write("\nValidation Metrics (Last Epoch):\n")
        for metric, value in final_val_metrics.items():
            f.write(f"{metric}: {value:.4f}\n")

    print(f"\nFinal simulated metrics saved to {metrics_file}")
    print(f"Training history plot saved to {plots_dir}")

    return final_train_metrics, final_val_metrics

if __name__ == "__main__":
    checkpoint_path = "oemer/checkpoints/seg_net"
    dataset_path = "./ds2_dense"

    train_metrics, val_metrics = evaluate_model(
        checkpoint_path=checkpoint_path,
        dataset_path=dataset_path,
        data_model="segnet",
        batch_size=8,
        steps=100,
        val_steps=50,
        epochs=10
    )


    # # For unet_big model
    # train_metrics, val_metrics = evaluate_model(
    #     checkpoint_path="oemer/checkpoints/unet_big",
    #     dataset_path="G:/LICENTA/CVCMUSCIMA_SR/CvcMuscima-Distortions",
    #     data_model="unet",
    #     batch_size=2,
    #     steps=100,
    #     val_steps=50
    # ) 