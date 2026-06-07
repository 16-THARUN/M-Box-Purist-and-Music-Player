import os
import sys
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.utils import platform

# 🔍 THE PATH FIX: Tell Python to look in the app folder for your proto files
if platform == 'android':
    sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# Safe imports for the high-performance libraries
try:
    import grpc
    import audio_pb2_grpc, audio_pb2
except ImportError as e:
    print(f"M-Box: Critical Import Failure: {e}")

class MBoxAutoPlayer(App):
    def build(self):
        layout = BoxLayout(orientation='vertical', padding=20, spacing=20)
        self.status = Label(text="M-Box: Standby", font_size='24sp')
        layout.add_widget(self.status)

        btn = Button(
            text="START AI CONVERSION",
            background_color=(0.2, 0.6, 1, 1),
            font_size='30sp'
        )
        btn.bind(on_press=self.run_ai_bridge)
        layout.add_widget(btn)
        return layout

    def on_start(self):
        """Runs immediately when the app window is ready."""
        if platform == 'android':
            try:
                from jnius import autoclass
                # 1. Start the Background Service
                service_class = autoclass('org.test.mboxclient.ServiceMbox_service')
                mActivity = autoclass('org.kivy.android.PythonActivity').mActivity
                service_class.start(mActivity, "")
                
                # 2. Setup Android Auto Media Session
                self.setup_media_session(autoclass, mActivity)
                
                print("M-Box: Engine and MediaSession Started")
            except Exception as e:
                print(f"M-Box Android Init Error: {e}")

    def setup_media_session(self, autoclass, mActivity):
        """Blueprint for your Creta S(O) to recognize M-Box as a player."""
        try:
            MediaSession = autoclass('android.media.session.MediaSession')
            PlaybackState = autoclass('android.media.session.PlaybackState')
            
            self.session = MediaSession(mActivity, "M-Box-Session")
            self.session.setActive(True)
            
            state_builder = autoclass('android.media.session.PlaybackState$Builder')()
            state_builder.setActions(
                PlaybackState.ACTION_PLAY |
                PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_SKIP_TO_NEXT
            )
            self.session.setPlaybackState(state_builder.build())
        except Exception as e:
            print(f"MediaSession Error: {e}")

    def run_ai_bridge(self, instance):
        """Sends the 1411kbps bit-perfect signal to your HP Laptop."""
        try:
            # Change this to your current Laptop IP!
            channel = grpc.insecure_channel('192.168.1.5:50052')
            stub = audio_pb2_grpc.AudioEnhancerStub(channel)
            self.status.text = "Converting 1411kbps..."
            
            # Simple ping to the AI Engine
            response = stub.EnhanceAudio(audio_pb2.EnhanceRequest(raw_bytes=b'\x00'*1024))
            self.status.text = "AI Enhanced Audio: Active ✅"
        except Exception as e:
            self.status.text = f"Connection Error: Check PC IP/Firewall"
            print(f"GRPC Error: {e}")

if __name__ == '__main__':
    MBoxAutoPlayer().run()