import tensorflow as tf
import tensorflow.keras.layers as L


def tiny_conv(win_size=256, out_class=4):
    """An extremely lightweight model for memory-constrained environments."""
    inp = L.Input(shape=(win_size, win_size, 3))
    tensor = tf.cast(inp, tf.float32)
    
    # Initial convolution with very few filters
    x = L.Conv2D(16, (3, 3), padding='same', dtype=tf.float32)(tensor)
    x = L.BatchNormalization()(x)
    x = L.Activation('relu')(x)
    
    # Downsample
    x = L.MaxPooling2D((2, 2))(x)
    
    # Middle convolution
    x = L.Conv2D(32, (3, 3), padding='same', dtype=tf.float32)(x)
    x = L.BatchNormalization()(x)
    x = L.Activation('relu')(x)
    
    # Upsample
    x = L.UpSampling2D((2, 2))(x)
    
    # Final convolution
    x = L.Conv2D(16, (3, 3), padding='same', dtype=tf.float32)(x)
    x = L.BatchNormalization()(x)
    x = L.Activation('relu')(x)
    
    # Output layer
    out = L.Conv2D(out_class, (1, 1), activation='sigmoid', padding='same', dtype=tf.float32)(x)
    
    return tf.keras.Model(inputs=inp, outputs=out) 