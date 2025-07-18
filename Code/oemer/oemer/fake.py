train_metrics = model.evaluate(train_data, steps=100, return_dict=True)
val_metrics = model.evaluate(val_data, steps=50, return_dict=True)

history = {
    'loss': [train_metrics['loss']],
    'val_loss': [val_metrics['loss']],
    'accuracy': [train_metrics['accuracy']],
    'val_accuracy': [val_metrics['accuracy']],
    'dice_coef': [train_metrics['dice_coef']],
    'val_dice_coef': [val_metrics['dice_coef']]
}
import matplotlib.pyplot as plt

for metric in ['loss', 'accuracy', 'dice_coef']:
    plt.plot(history[metric], label=f'train {metric}')
    plt.plot(history[f'val_{metric}'], label=f'val {metric}')
    plt.title(f'{metric} over epochs (simulated)')
    plt.legend()
    plt.show()
