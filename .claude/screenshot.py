"""
Simple screenshot capture tool
Takes a screenshot and saves it to the Downloads folder
"""
from PIL import ImageGrab
from datetime import datetime
import os

def take_screenshot():
    # Get the Downloads folder path
    downloads_path = os.path.expanduser("~\\Downloads")

    # Generate timestamp for unique filename
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"screenshot_{timestamp}.png"
    filepath = os.path.join(downloads_path, filename)

    # Capture the screen
    screenshot = ImageGrab.grab()

    # Save the screenshot
    screenshot.save(filepath)

    print(f"Screenshot saved to: {filepath}")
    return filepath

if __name__ == "__main__":
    filepath = take_screenshot()