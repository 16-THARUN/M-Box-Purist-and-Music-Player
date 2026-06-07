"""
mbox_gui.py – Tkinter desktop GUI for M-BOX2 Crystal Master

FIXES vs original:
  - BUG 8:  Progress bar now updates per-track (0→100) via the callback protocol.
            Original only jumped to 20 at start and 100 at end.
  - BUG 9:  run_mbox_pipeline now accepts progress_callback; every log line and
            every __PROGRESS__N signal is routed back here thread-safely via
            root.after() instead of direct widget writes from the worker thread.
  - General: start/stop state is managed correctly; log auto-scrolls.
"""

import tkinter as tk
from tkinter import ttk, messagebox
import threading
import os

from mbox_master import run_mbox_pipeline


class MboxGUI:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("M-BOX2 | Crystal Clear 1411 kbps")
        self.root.geometry("620x560")
        self.root.configure(bg="#1a1a1a")
        self.root.resizable(False, False)

        # ttk style
        style = ttk.Style()
        style.theme_use('clam')
        style.configure("Cyan.Horizontal.TProgressbar",
                        thickness=18, troughcolor='#2a2a2a', background='#00d1ff')

        self._build_ui()

    # ------------------------------------------------------------------
    # UI CONSTRUCTION
    # ------------------------------------------------------------------
    def _build_ui(self):
        R = self.root

        tk.Label(R, text="M-BOX2  CRYSTAL MASTER",
                 font=("Consolas", 18, "bold"), fg="#00d1ff", bg="#1a1a1a").pack(pady=(18, 4))
        tk.Label(R, text="1411 kbps · 44.1 kHz · 16-bit FLAC",
                 font=("Consolas", 9), fg="#555555", bg="#1a1a1a").pack()

        # Source selector
        tk.Label(R, text="Mastering Source:", fg="#aaaaaa", bg="#1a1a1a",
                 font=("Arial", 10)).pack(pady=(14, 2))

        self.source_var = tk.StringVar(value="1")
        radio_frame = tk.Frame(R, bg="#1a1a1a")
        radio_frame.pack()
        for text, val in [("YouTube Music", "1"), ("Amazon / Spotify Bridge", "2")]:
            tk.Radiobutton(radio_frame, text=text, variable=self.source_var, value=val,
                           bg="#1a1a1a", fg="#00d1ff", selectcolor="#2a2a2a",
                           activebackground="#1a1a1a", font=("Arial", 9)
                           ).pack(side="left", padx=18)

        # URL input
        tk.Label(R, text="Playlist URL or Song Name:", fg="#aaaaaa", bg="#1a1a1a").pack(pady=(12, 0))
        self.url_entry = tk.Entry(R, width=62, font=("Consolas", 10),
                                  bg="#2a2a2a", fg="white", insertbackground="white",
                                  relief="flat", bd=4)
        self.url_entry.pack(pady=8, ipady=4)
        self.url_entry.insert(0, "Hans Zimmer Time")

        # Log console
        self.log_box = tk.Text(R, height=12, width=72, bg="#0a0a0a", fg="#00ff88",
                               font=("Consolas", 9), relief="flat", bd=0,
                               state="disabled")
        self.log_box.pack(pady=(4, 6), padx=20)

        # Progress bar
        self.progress = ttk.Progressbar(R, style="Cyan.Horizontal.TProgressbar",
                                        orient="horizontal", length=520,
                                        mode="determinate", maximum=100)
        self.progress.pack(pady=4)

        # Progress label
        self.prog_label = tk.Label(R, text="Idle", fg="#555555", bg="#1a1a1a",
                                   font=("Consolas", 9))
        self.prog_label.pack()

        # Buttons
        btn_frame = tk.Frame(R, bg="#1a1a1a")
        btn_frame.pack(pady=12)

        self.start_btn = tk.Button(btn_frame, text="▶  START MASTERING",
                                   command=self.start_thread,
                                   bg="#00d1ff", fg="#000000",
                                   font=("Arial", 10, "bold"), width=22,
                                   relief="flat", bd=0, cursor="hand2")
        self.start_btn.pack(side="left", padx=10)

        tk.Button(btn_frame, text="📂  Open Output Folder",
                  command=self.open_folder,
                  bg="#2a2a2a", fg="white", width=18,
                  relief="flat", bd=0, cursor="hand2"
                  ).pack(side="left", padx=10)

    # ------------------------------------------------------------------
    # LOGGING (thread-safe via root.after)
    # ------------------------------------------------------------------
    def _append_log(self, message: str):
        self.log_box.config(state="normal")
        self.log_box.insert(tk.END, f"> {message}\n")
        self.log_box.see(tk.END)
        self.log_box.config(state="disabled")

    def log(self, message: str):
        """Called from worker thread – routes to main thread safely."""
        self.root.after(0, lambda m=message: self._handle_callback(m))

    def _handle_callback(self, message: str):
        # FIX BUG 8 / 9: parse __PROGRESS__N signals for the progress bar
        if message.startswith("__PROGRESS__"):
            try:
                pct = int(message.replace("__PROGRESS__", ""))
                self.progress['value'] = pct
                self.prog_label.config(text=f"Processing… {pct}%",
                                       fg="#00d1ff" if pct < 100 else "#00ff88")
            except ValueError:
                pass
        else:
            self._append_log(message)

    # ------------------------------------------------------------------
    # ACTIONS
    # ------------------------------------------------------------------
    def open_folder(self):
        path = os.path.abspath("downloads/enhanced")
        os.makedirs(path, exist_ok=True)
        os.startfile(path)  # Windows; on macOS/Linux swap with subprocess.run(["open"/"xdg-open", path])

    def start_thread(self):
        url  = self.url_entry.get().strip()
        mode = self.source_var.get()

        if not url:
            messagebox.showerror("Error", "Please enter a search query or URL.")
            return

        self.start_btn.config(state="disabled")
        self.progress['value'] = 0
        self.prog_label.config(text="Starting…", fg="#00d1ff")

        self.log_box.config(state="normal")
        self.log_box.delete(1.0, tk.END)
        self.log_box.config(state="disabled")

        threading.Thread(
            target=self._pipeline_worker, args=(url, mode), daemon=True
        ).start()

    def _pipeline_worker(self, url: str, mode: str):
        try:
            source_name = "YouTube Music" if mode == "1" else "Amazon Bridge"
            self.log(f"Initialising MBOX2 {source_name} engine…")
            run_mbox_pipeline(url, mode, progress_callback=self.log)
            self.log("🎯 Batch complete! 1411 kbps Crystal FLAC ready.")
            self.root.after(0, lambda: messagebox.showinfo(
                "Done", f"All tracks from {source_name} mastered successfully!\n"
                        f"Output: downloads/enhanced/"))
        except Exception as e:
            self.log(f"❌ Error: {e}")
        finally:
            self.root.after(0, lambda: self.start_btn.config(state="normal"))
            self.log("__PROGRESS__100")


if __name__ == "__main__":
    root = tk.Tk()
    app  = MboxGUI(root)
    root.mainloop()