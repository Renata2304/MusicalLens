import os
from music21 import converter, midi
from io import BytesIO

def convert_musicxml_content_to_midi_bytes(musicxml_content: str) -> bytes:
    try:
        score = converter.parse(musicxml_content)

        combined_midi = midi.MidiFile()

        for part in score.parts:
            mf = midi.translate.streamToMidiFile(part)
            for track in mf.tracks:
                combined_midi.tracks.append(track)

        midi_buffer = BytesIO()
        combined_midi.open(midi_buffer, 'wb')
        combined_midi.write()
        combined_midi.close()

        midi_bytes = midi_buffer.getvalue()
        midi_buffer.close()

        return midi_bytes

    except Exception as e:
        raise Exception(f"Error converting MusicXML to MIDI: {e}")
