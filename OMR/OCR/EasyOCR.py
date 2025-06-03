import os
import easyocr
import cv2
import time

def ocr_with_visualization(image_path, output_path="ocr_result_with_boxes.png"):
    """
    Process image with OCR and visualize results
    """
    # Initialize EasyOCR reader
    reader = easyocr.Reader(['en'])
    
    # Read image
    image = cv2.imread(image_path)
    if image is None:
        raise ValueError(f"Failed to read image: {image_path}")
    
    # Perform OCR
    start_time = time.time()
    results = reader.readtext(image)
    processing_time = time.time() - start_time
    
    # Process results
    recognized_text = []
    
    # Draw bounding boxes and text
    for (bbox, text, prob) in results:
        (tl, tr, br, bl) = bbox
        tl = (int(tl[0]), int(tl[1]))
        br = (int(br[0]), int(br[1]))
        
        # Draw rectangle
        cv2.rectangle(image, tl, br, (0, 255, 0), 2)
        
        # Add text with confidence
        text_with_conf = f"{text} ({prob:.2f})"
        cv2.putText(image, text_with_conf, (tl[0], tl[1] - 10),
                   cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
        
        recognized_text.append(text)
    
    # Save annotated image
    cv2.imwrite(output_path, image)
    
    print(f"Processing time: {processing_time:.2f} seconds")
    return " ".join(recognized_text)

def main():
    # Process image
    image_to_process = "OCR/sample_page.png"
    if os.path.exists(image_to_process):
        print("Processing image with EasyOCR...")
        recognized_text = ocr_with_visualization(image_to_process)
        print("\nRecognized text:")
        print(recognized_text)
        print("\nOutput image with boxes and labels saved as ocr_result_with_boxes.png")
    else:
        print(f"Image file {image_to_process} not found")

if __name__ == "__main__":
    main()
