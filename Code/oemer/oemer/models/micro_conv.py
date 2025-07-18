import tensorflow as tf
import tensorflow.keras.layers as L


def micro_conv(win_size=256, out_class=4):
    """An extremely minimal model for memory-constrained environments."""
    inp = L.Input(shape=(win_size, win_size, 3))
    tensor = tf.cast(inp, tf.float32)
    
    # Single convolution with minimal filters
    x = L.Conv2D(8, (3, 3), padding='same', dtype=tf.float32)(tensor)
    x = L.BatchNormalization()(x)
    x = L.Activation('relu')(x)
    
    # Output layer
    out = L.Conv2D(out_class, (1, 1), activation='sigmoid', padding='same', dtype=tf.float32)(x)
    
    return tf.keras.Model(inputs=inp, outputs=out) 