import os
import threading

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.scrollview import ScrollView
from kivy.uix.progressbar import ProgressBar
from kivy.uix.popup import Popup
from kivy.uix.filechooser import FileChooserListView
from kivy.clock import Clock
from kivy.graphics import Color, Rectangle

from mbox_master import run_mbox_pipeline


class MboxGui(BoxLayout):
    def __init__(self, **kwargs):
        super().__init__(orientation='vertical', padding=20, spacing=10, **kwargs)

        self.is_running = False    # 🛑 State flag for Emergency Stop

        with self.canvas.before:
            Color(0.08, 0.08, 0.08, 1)
            self.bg_rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

        # Header
        self.add_widget(Label(
            text="[b][color=00d1ff]M-BOX2  CRYSTAL MASTER[/color][/b]",
            markup=True, font_size='22sp', size_hint_y=None, height=55,
        ))
        self.add_widget(Label(
            text="1411 kbps · 44.1 kHz · 16-bit FLAC",
            font_size='12sp', color=(0.4, 0.4, 0.4, 1),
            size_hint_y=None, height=22,
        ))

        # URL / Search / CSV input row (Toggle Buttons Removed!)
        self.add_widget(Label(text="Target URL, Search Query, or CSV Path:", size_hint_y=None, height=26,
                              color=(0.7, 0.7, 0.7, 1), font_size='13sp'))
        
        url_row = BoxLayout(orientation='horizontal', spacing=10, size_hint_y=None, height=44)
        self.url_input = TextInput(
            text="",
            multiline=False, size_hint_y=None, height=44,
            background_color=(0.15, 0.15, 0.15, 1),
            foreground_color=(1, 1, 1, 1),
            cursor_color=(0, 0.82, 1, 1),
            font_size='14sp',
        )
        self.browse_btn = Button(
            text="📁", size_hint_x=None, width=50,
            background_color=(0.3, 0.3, 0.3, 1)
        )
        self.browse_btn.bind(on_press=self.open_file_chooser)
        url_row.add_widget(self.url_input)
        url_row.add_widget(self.browse_btn)
        self.add_widget(url_row)

        # Progress bar 
        self.progress_bar = ProgressBar(max=100, value=0, size_hint_y=None, height=12)
        self.add_widget(self.progress_bar)

        self.progress_label = Label(
            text="Idle", size_hint_y=None, height=22,
            color=(0.35, 0.35, 0.35, 1), font_size='11sp',
        )
        self.add_widget(self.progress_label)

        # Start & Stop Buttons
        btn_row = BoxLayout(orientation='horizontal', spacing=10, size_hint_y=None, height=56)
        
        self.start_btn = Button(
            text="▶  START CRYSTAL MASTERING",
            background_color=(0, 0.82, 1, 1), font_size='16sp',
        )
        self.start_btn.bind(on_press=self.start_processing)
        
        self.stop_btn = Button(
            text="⏹ STOP", size_hint_x=None, width=100,
            background_color=(1, 0.2, 0.2, 1), font_size='14sp', disabled=True
        )
        self.stop_btn.bind(on_press=self.stop_processing)
        
        btn_row.add_widget(self.start_btn)
        btn_row.add_widget(self.stop_btn)
        self.add_widget(btn_row)

        # Log console 
        self.scroll_view = ScrollView(size_hint=(1, 1))
        self.log_label = Label(
            text="System ready…\n",
            halign='left', valign='top',
            size_hint_y=None,
            color=(0.1, 1, 0.5, 1),
            markup=True, font_size='12sp',
        )
        self.log_label.bind(texture_size=self.log_label.setter('size'))
        self.scroll_view.add_widget(self.log_label)
        self.add_widget(self.scroll_view)

    # ------------------------------------------------------------------
    def _update_rect(self, instance, value):
        self.bg_rect.pos  = instance.pos
        self.bg_rect.size = instance.size

    def open_file_chooser(self, instance):
        """Native Kivy Popup for selecting a CSV file."""
        content = BoxLayout(orientation='vertical', spacing=10)
        
        fc = FileChooserListView(filters=['*.csv'], path=os.path.expanduser('~'))
        
        btn_row = BoxLayout(size_hint_y=None, height=40, spacing=10)
        sel_btn = Button(text="Select", background_color=(0, 0.82, 1, 1))
        can_btn = Button(text="Cancel", background_color=(0.3, 0.3, 0.3, 1))
        btn_row.add_widget(sel_btn)
        btn_row.add_widget(can_btn)
        
        content.add_widget(fc)
        content.add_widget(btn_row)
        
        popup = Popup(title="Select CSV Ledger", content=content, size_hint=(0.9, 0.9))
        
        def on_select(*args):
            if fc.selection:
                self.url_input.text = fc.selection[0]
            popup.dismiss()
            
        sel_btn.bind(on_press=on_select)
        can_btn.bind(on_press=popup.dismiss)
        popup.open()

    # ------------------------------------------------------------------
    # THREAD-SAFE LOGGING
    # ------------------------------------------------------------------
    def _schedule_log(self, message: str):
        """Route any string to the main thread via Clock (thread-safe)."""
        Clock.schedule_once(lambda dt, m=message: self._handle_callback(m))

    def _handle_callback(self, message: str):
        """Runs on the main thread."""
        if message.startswith("__PROGRESS__"):
            try:
                pct = int(message.replace("__PROGRESS__", ""))
                self.progress_bar.value = pct
                self.progress_label.text  = f"Processing… {pct}%"
                self.progress_label.color = (0, 0.82, 1, 1) if pct < 100 else (0.1, 1, 0.5, 1)
            except ValueError:
                pass
        else:
            self.log_label.text += f"> {message}\n"
            Clock.schedule_once(lambda dt: setattr(self.scroll_view, 'scroll_y', 0), 0.1)

    # ------------------------------------------------------------------
    def start_processing(self, *_):
        query = self.url_input.text.strip()
        if not query:
            self._schedule_log("[color=ff4444]Error: no input provided.[/color]")
            return

        self.is_running = True
        self.start_btn.disabled = True
        self.stop_btn.disabled = False
        self.progress_bar.value = 0
        self.log_label.text     = ""
        self._schedule_log(f"🛰️  Initiating Universal Pipeline…")

        threading.Thread(
            target=self._pipeline_worker,
            args=(query,),
            daemon=True,
        ).start()

    def stop_processing(self, *_):
        self.is_running = False
        self.stop_btn.disabled = True
        self._schedule_log("⚠️ [color=ffaa00]Stop signal sent. Aborting after current track finishes...[/color]")

    def _pipeline_worker(self, query: str):
        try:
            run_mbox_pipeline(
                target_input=query, 
                progress_callback=self._schedule_log,
                stop_checker=lambda: not self.is_running
            )
            if self.is_running:
                self._schedule_log("🎯 [color=00ff88]BATCH SUCCESS![/color] Check downloads/enhanced/")
        except Exception as e:
            self._schedule_log(f"❌ [color=ff4444]Error: {e}[/color]")
        finally:
            self.is_running = False
            def reset_ui(dt):
                self.start_btn.disabled = False
                self.stop_btn.disabled = True
            Clock.schedule_once(reset_ui)
            self._schedule_log("__PROGRESS__100")


class MboxApp(App):
    def build(self):
        self.title = "M-BOX2 Crystal Master"
        return MboxGui()


if __name__ == "__main__":
    MboxApp().run()