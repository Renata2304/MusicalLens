# server.py
from flask import Flask, request, jsonify
import cv2
import numpy as np
import base64
import io
import sys
import os
import subprocess
import tempfile

import music21
from io import BytesIO 

app = Flask(__name__)

@app.route('/process_image', methods=['POST'])
def process_image():
    """
    Endpoint pentru a primi o imagine Base64, a salva imaginea temporar,
    a rula comanda `oemer` pe ea și a returna MusicXML.
    """
    if not request.is_json:
        return jsonify({"error": "Request must be JSON"}), 400

    data = request.get_json()
    image_base64_string = data.get('image_base64')
    oemer_extra_args = data.get('oemer_extra_args', [])

    if not image_base64_string:
        return jsonify({"error": "Missing 'image_base64' in request"}), 400

    temp_image_path = None
    output_xml_path = None

    try:
        image_bytes = base64.b64decode(image_base64_string)
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img is None:
            return jsonify({"error": "Could not decode image from Base64"}), 400

        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as temp_img_file:
            temp_image_path = temp_img_file.name
            cv2.imwrite(temp_image_path, img)
        
        output_xml_name = os.path.splitext(os.path.basename(temp_image_path))[0] + ".musicxml"
        output_xml_path = os.path.join(tempfile.gettempdir(), output_xml_name)

        command = ["oemer", temp_image_path, "-o", output_xml_path] + oemer_extra_args

        app.logger.info(f"Executing oemer command: {' '.join(command)}")
        
        process = subprocess.run(command, capture_output=True, text=True, check=True)

        if not os.path.exists(output_xml_path):
            app.logger.error(f"Oemer did not produce output file at: {output_xml_path}. Stdout: {process.stdout} Stderr: {process.stderr}")
            return jsonify({
                "error": "Oemer did not produce MusicXML output.",
                "oemer_stdout": process.stdout,
                "oemer_stderr": process.stderr
            }), 500

        with open(output_xml_path, 'r', encoding='utf-8') as f:
            music_xml_string = f.read()

        # Returnează MusicXML-ul și output-ul oemer
        return jsonify({
            "music_xml": music_xml_string,
            "status": "success",
            "oemer_stdout": process.stdout,
            "oemer_stderr": process.stderr
        }), 200

    except subprocess.CalledProcessError as e:
        app.logger.error(f"Oemer command failed with error code {e.returncode}: {e.stderr}", exc_info=True)
        return jsonify({
            "error": f"Oemer command failed: {e.stderr}",
            "return_code": e.returncode,
            "oemer_stdout": e.stdout,
            "oemer_stderr": e.stderr
        }), 500
    except Exception as e:
        app.logger.error(f"Error during Oemer processing: {e}", exc_info=True)
        return jsonify({"error": f"Internal server error: {str(e)}"}), 500
    finally:
        # Asigură-te că ștergi fișierele temporare
        if temp_image_path and os.path.exists(temp_image_path):
            os.remove(temp_image_path)
            app.logger.info(f"Removed temporary image file: {temp_image_path}")
        if output_xml_path and os.path.exists(output_xml_path):
            os.remove(output_xml_path)
            app.logger.info(f"Removed temporary MusicXML file: {output_xml_path}")

@app.route('/convert_musicxml_to_midi', methods=['POST'])
def convert_musicxml_to_midi():
    try:
        data = request.get_json()
        music_xml_content = data.get('music_xml_content')

        # Parse the MusicXML content
        score = music21.converter.parse(music_xml_content)

        # Create a temporary file to write the MIDI data
        # Use tempfile.NamedTemporaryFile to get a unique, temporary filename
        with tempfile.NamedTemporaryFile(delete=False, suffix=".mid") as temp_midi_file:
            temp_midi_filename = temp_midi_file.name

        try:
            # Write the MIDI score to the temporary file
            score.write('midi', fp=temp_midi_filename)

            # Read the MIDI content from the temporary file
            with open(temp_midi_filename, 'rb') as f:
                midi_bytes = f.read()

            # Encode the MIDI bytes to Base64
            midi_base64 = base64.b64encode(midi_bytes).decode('utf-8')

            return jsonify({"midi_base64": midi_base64}), 200
        finally:
            # Ensure the temporary file is deleted
            if os.path.exists(temp_midi_filename):
                os.remove(temp_midi_filename)

    except Exception as e:
        app.logger.error(f"Error during MusicXML to MIDI conversion: {e}", exc_info=True)
        return jsonify({"error": f"Error during MusicXML to MIDI conversion: {e}"}), 500



if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)