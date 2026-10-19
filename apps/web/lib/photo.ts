// A camera photo → a JPEG at most 1600 px on its longer side (Learning Notes 78): ~300 KB instead of ~4 MB on airport
// data, and drawing it on a canvas leaves the EXIF data (like the GPS location) behind before it leaves the phone.
// The server checks and re-encodes it again anyway: this is for speed and privacy, not security.
export async function shrinkPhoto(file: File, maxSide = 1600): Promise<Blob> {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" }); // upright, as the camera meant
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("toBlob failed"))), "image/jpeg", 0.85),
  );
}
