import soundfile as sf
import numpy as np
import os
from scipy import signal


class AudioUpscaler:
    def __init__(self, output_path="downloads/enhanced", progress_callback=None):
        self.output_path = output_path
        self.progress_callback = progress_callback  # FIX: callback support added
        if not os.path.exists(self.output_path):
            os.makedirs(self.output_path)

    def _log(self, msg):
        print(msg)
        if self.progress_callback:
            self.progress_callback(msg)

    def process_to_1411(self, input_file):
        """
        Transforms raw audio into Crystal Clear 1411kbps FLAC.
        Standard: 44.1kHz, 16-bit, Stereo.

        FIXES applied:
          - BUG 4: resample now handles mono AND multi-channel correctly
            (original code called signal.resample on 2-D array with wrong num_samples)
          - Stereo force moved BEFORE resample so shape is always (N,2) downstream
          - All DSP math stays float64 internally; only casted to int16 at sf.write
        """
        self._log(f"💎 Crystal Remastering: {os.path.basename(input_file)}")

        # 1. Load raw WAV
        data, samplerate = sf.read(input_file, always_2d=True)  # always_2d=True -> shape (N,C)

        # 2. Internal precision
        data = data.astype(np.float64)

        # 3. Force stereo BEFORE any resampling so all operations are on (N,2)
        #    FIX: original code forced stereo AFTER resample which broke the (N,) mono path
        if data.shape[1] == 1:
            data = np.hstack((data, data))          # mono  -> stereo
        elif data.shape[1] > 2:
            data = data[:, :2]                       # >2ch  -> take first two channels

        # 4. Resample to 44.1 kHz (per-channel to avoid shape mismatch)
        #    FIX: original used signal.resample(data, num_samples) on a 2-D array where
        #         num_samples was computed from len(data) which equals rows – correct only
        #         for 1-D.  scipy.signal.resample axis=0 fixes this cleanly.
        target_sr = 44100
        if samplerate != target_sr:
            num_samples = int(data.shape[0] * target_sr / samplerate)
            data = signal.resample(data, num_samples, axis=0)

        # 5. Crystal DSP Chain
        # --- 30 Hz high-pass (remove sub-bass mud) ---
        sos_hp = signal.butter(4, 30, 'hp', fs=target_sr, output='sos')
        enhanced = signal.sosfilt(sos_hp, data, axis=0)

        # --- 12 kHz "Air" boost (high-shelf approximation) ---
        sos_air = signal.butter(2, 12000, 'hp', fs=target_sr, output='sos')
        air_signal = signal.sosfilt(sos_air, enhanced, axis=0)
        enhanced = enhanced + (air_signal * 0.20)   # +20% air shimmer

        # --- Mild dynamic expansion ---
        enhanced = np.sign(enhanced) * (np.abs(enhanced) ** 1.03)

        # --- Stereo widening (MS matrix) ---
        width = 1.12
        mid  = (enhanced[:, 0] + enhanced[:, 1]) / 2.0
        side = (enhanced[:, 0] - enhanced[:, 1]) / 2.0
        enhanced[:, 0] = mid + (side * width)
        enhanced[:, 1] = mid - (side * width)

        # 6. Peak normalise – clipping protection
        max_val = np.max(np.abs(enhanced))
        if max_val > 0:
            enhanced = enhanced * (0.98 / max_val)

        # 7. Export as 16-bit FLAC  →  44100 * 16 * 2 = 1 411 200 bps ≈ 1411 kbps
        base = os.path.splitext(os.path.basename(input_file))[0]
        output_filename = f"{base}_CRYSTAL.flac"
        output_file = os.path.join(self.output_path, output_filename)
        sf.write(output_file, enhanced, target_sr, subtype='PCM_16')

        self._log(f"✅ Done: {output_filename}  [1411 kbps · 44.1 kHz · 16-bit stereo]")
        return output_file