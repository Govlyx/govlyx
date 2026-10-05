export async function fetchPincodeFromCoordinates(
  lat: number,
  lon: number,
): Promise<string | null> {
  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=18&addressdetails=1`,
      {
        headers: {
          'User-Agent': 'Govlyx-Web-Client/1.0 (contact@govlyx.com)',
        },
      },
    );
    if (!response.ok) return null;
    const data = await response.json();
    return data?.address?.postcode || null;
  } catch (error) {
    console.error('Geocoding failed:', error);
    return null;
  }
}
