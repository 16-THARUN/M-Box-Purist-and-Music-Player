import numpy as np
from scipy.signal import butter, lfilter, fftconvolve
from scipy.io import wavfile
import os

def butter_filter(data, cutoff, fs, btype='low', order=2):
    nyq = 0.5 * fs
    normal_cutoff = min(cutoff / nyq, 0.99)
    b, a = butter(order, normal_cutoff, btype=btype, analog=False)
    left = lfilter(b, a, data[:, 0])
    right = lfilter(b, a, data[:, 1])
    return np.column_stack((left, right))

def mid_side_process(signal, mid_gain, side_gain, delay_samples=0):
    mid = (signal[:, 0] + signal[:, 1]) / 2.0
    side = (signal[:, 0] - signal[:, 1]) / 2.0

    mid = mid * mid_gain
    side = side * side_gain

    if delay_samples > 0:
        side = np.pad(side, (delay_samples, 0))[:-delay_samples]

    left = mid + side
    right = mid - side
    return np.column_stack((left, right))

def process_file(input_path, output_path, mode, ir_path=""):
    try:
        # 1. Load Audio using SciPy instead of soundfile
        fs, signal = wavfile.read(input_path)
        
        # Convert integer PCM to float32 for processing (-1.0 to 1.0)
        if signal.dtype != np.float32 and signal.dtype != np.float64:
            signal = signal.astype(np.float32) / np.iinfo(signal.dtype).max

        if signal.ndim == 1:
            signal = np.column_stack((signal, signal))

        # 2. Apply Dolby Spatial Modes
        if mode == "DOLBY_OPEN":
            signal = mid_side_process(signal, mid_gain=0.9, side_gain=1.6, delay_samples=int(fs * 0.008))
        elif mode == "DOLBY_RICH":
            low_end = butter_filter(signal, cutoff=250, fs=fs, btype='low')
            signal = signal + (low_end * 0.45)
            signal = mid_side_process(signal, mid_gain=1.0, side_gain=1.25)
        elif mode == "DOLBY_FOCUSED":
            narrowed = mid_side_process(signal, mid_gain=1.3, side_gain=0.5)
            signal = butter_filter(narrowed, cutoff=150, fs=fs, btype='high')
            
        # 3. Apply Dirac Audio Modes
        elif mode == "DIRAC_MOVIE":
            sub = butter_filter(signal, cutoff=80, fs=fs, btype='low')
            highs = butter_filter(signal, cutoff=6000, fs=fs, btype='high')
            signal = signal + (sub * 0.5) + (highs * 0.3)
        elif mode == "DIRAC_GAME":
            cues = butter_filter(signal, cutoff=3500, fs=fs, btype='high')
            signal = signal + (cues * 0.6)

        # 4. Dirac Phase Correction (Convolution)
        if mode.startswith("DIRAC") and ir_path and os.path.exists(ir_path):
            ir_fs, ir_signal = wavfile.read(ir_path)
            # Convert IR to float32 if needed
            if ir_signal.dtype != np.float32 and ir_signal.dtype != np.float64:
                ir_signal = ir_signal.astype(np.float32) / np.iinfo(ir_signal.dtype).max
                
            if ir_signal.ndim == 1:
                ir_signal = np.column_stack((ir_signal, ir_signal))
            left = fftconvolve(signal[:, 0], ir_signal[:, 0], mode='same')
            right = fftconvolve(signal[:, 1], ir_signal[:, 1], mode='same')
            signal = np.column_stack((left, right))

        # 5. Prevent Clipping & Export using SciPy
        max_val = np.max(np.abs(signal))
        if max_val > 1.0:
            signal = signal / max_val
            
        # Convert back to 16-bit PCM for export
        signal_int16 = np.int16(signal * 32767)
        wavfile.write(output_path, fs, signal_int16)
        
        return True
    except Exception as e:
        print(f"DSP Error: {e}")
        return False
