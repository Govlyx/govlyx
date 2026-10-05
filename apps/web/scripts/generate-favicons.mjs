import sharp from 'sharp';
import fs from 'fs';
import path from 'path';

const svgContent = `<svg width="200" height="200" viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
  <!-- Blue circle background -->
  <circle cx="100" cy="100" r="100" fill="#1D4ED8"/>
  
  <!-- All white elements, scaled up and centered -->
  <g transform="translate(100, 102) scale(0.32) translate(-256, -290)">
    <!-- Building/columns top -->
    <g fill="#FFFFFF" transform="translate(0, -6)">
      <path d="M256 150c-40 0-72 32-72 72v20h144v-20c0-40-32-72-72-72z" />
      <rect x="220" y="242" width="72" height="16" />
      <rect x="204" y="220" width="12" height="40" />
      <rect x="296" y="220" width="12" height="40" />
    </g>
    <!-- Decorative circles -->
    <g fill="#FFFFFF" transform="translate(0, -6)">
      <circle cx="170" cy="210" r="6" />
      <circle cx="196" cy="230" r="4" />
      <circle cx="342" cy="210" r="6" />
      <circle cx="318" cy="230" r="4" />
      <circle cx="256" cy="190" r="5" />
    </g>
    <!-- Horizontal bar -->
    <path fill="#FFFFFF" d="M150 300h212l-8 16H158z" />
    <!-- Columns -->
    <g fill="#FFFFFF">
      <rect x="248" y="300" width="16" height="120" />
      <rect x="198" y="300" width="16" height="80" />
      <rect x="298" y="300" width="16" height="80" />
    </g>
    <!-- Bottom circles (column bases) -->
    <g fill="#FFFFFF">
      <circle cx="256" cy="440" r="18" />
      <circle cx="206" cy="380" r="20" />
      <circle cx="306" cy="380" r="20" />
    </g>
    <!-- Flag/banner top -->
    <g>
      <rect x="252" y="118" width="8" height="32" fill="#FFFFFF" />
      <path d="M260 118h45v22l-45-8z" fill="#FFFFFF" />
      <path d="M260 118l35 16l-35-6z" fill="#FFFFFF" opacity="0.4" />
    </g>
  </g>
</svg>`;

const ogSvgContent = `<svg width="1200" height="630" viewBox="0 0 1200 630" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <!-- Subtle glow for depth -->
    <radialGradient id="ogGlow" cx="85%" cy="85%" r="65%">
      <stop offset="0%" stop-color="#1D4ED8" stop-opacity="0.28" />
      <stop offset="60%" stop-color="#1D4ED8" stop-opacity="0.05" />
      <stop offset="100%" stop-color="#0B0F1A" stop-opacity="0" />
    </radialGradient>
    <radialGradient id="ogTopGlow" cx="15%" cy="15%" r="45%">
      <stop offset="0%" stop-color="#1E3A8A" stop-opacity="0.18" />
      <stop offset="100%" stop-color="#0B0F1A" stop-opacity="0" />
    </radialGradient>
  </defs>

  <!-- Background in #0B0F1A -->
  <rect width="1200" height="630" fill="#0B0F1A" />
  <rect width="1200" height="630" fill="url(#ogGlow)" />
  <rect width="1200" height="630" fill="url(#ogTopGlow)" />

  <!-- Logo Group (140x140) at (130, 140) -->
  <g transform="translate(130, 140)">
    <!-- Blue circle background -->
    <circle cx="70" cy="70" r="70" fill="#1D4ED8"/>
    
    <!-- All white elements -->
    <g transform="translate(70, 71.4) scale(0.224) translate(-256, -290)">
      <!-- Building/columns top -->
      <g fill="#FFFFFF" transform="translate(0, -6)">
        <path d="M256 150c-40 0-72 32-72 72v20h144v-20c0-40-32-72-72-72z" />
        <rect x="220" y="242" width="72" height="16" />
        <rect x="204" y="220" width="12" height="40" />
        <rect x="296" y="220" width="12" height="40" />
      </g>
      <!-- Decorative circles -->
      <g fill="#FFFFFF" transform="translate(0, -6)">
        <circle cx="170" cy="210" r="6" />
        <circle cx="196" cy="230" r="4" />
        <circle cx="342" cy="210" r="6" />
        <circle cx="318" cy="230" r="4" />
        <circle cx="256" cy="190" r="5" />
      </g>
      <!-- Horizontal bar -->
      <path fill="#FFFFFF" d="M150 300h212l-8 16H158z" />
      <!-- Columns -->
      <g fill="#FFFFFF">
        <rect x="248" y="300" width="16" height="120" />
        <rect x="198" y="300" width="16" height="80" />
        <rect x="298" y="300" width="16" height="80" />
      </g>
      <!-- Bottom circles (column bases) -->
      <g fill="#FFFFFF">
        <circle cx="256" cy="440" r="18" />
        <circle cx="206" cy="380" r="20" />
        <circle cx="306" cy="380" r="20" />
      </g>
      <!-- Flag/banner top -->
      <g>
        <rect x="252" y="118" width="8" height="32" fill="#FFFFFF" />
        <path d="M260 118h45v22l-45-8z" fill="#FFFFFF" />
        <path d="M260 118l35 16l-35-6z" fill="#FFFFFF" opacity="0.4" />
      </g>
    </g>
  </g>

  <!-- Brand Title & Tagline beside Logo -->
  <text x="300" y="208" fill="#FFFFFF" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-weight="800" font-size="70" letter-spacing="-0.02em">Govlyx</text>
  <text x="302" y="254" fill="#3B82F6" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-weight="700" font-size="20" letter-spacing="0.12em">HYPERLOCAL CIVIC PLATFORM</text>

  <!-- Headline -->
  <text x="130" y="375" fill="#FFFFFF" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-weight="700" font-size="38" letter-spacing="-0.01em">Connecting Every Indian to Their Neighbourhood &amp; Govt.</text>

  <!-- Description -->
  <text x="130" y="430" fill="#94A3B8" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-weight="400" font-size="23">Report local civic issues, track municipal resolutions, and connect by pincode.</text>
</svg>`;

async function generateFavicons() {
  const publicDir = path.resolve('public');

  // Write SVG files
  fs.writeFileSync(path.join(publicDir, 'logo.svg'), svgContent);
  fs.writeFileSync(path.join(publicDir, 'govlyx.svg'), svgContent);
  fs.writeFileSync(path.join(publicDir, 'favicon.svg'), svgContent);
  console.log('Saved logo.svg, govlyx.svg & favicon.svg');

  const emojiDir = path.join(publicDir, 'govlyx-emoji');
  if (fs.existsSync(emojiDir)) {
    fs.writeFileSync(path.join(emojiDir, 'govlyx.svg'), svgContent);
    fs.writeFileSync(path.join(emojiDir, 'govlyx-square.svg'), svgContent);
    console.log('Updated govlyx-emoji icons');
  }

  const sizes = [
    { name: 'favicon-16x16.png', size: 16 },
    { name: 'favicon-32x32.png', size: 32 },
    { name: 'favicon-48x48.png', size: 48 },
    { name: 'favicon-96x96.png', size: 96 },
    { name: 'apple-touch-icon.png', size: 180 },
    { name: 'favicon-192x192.png', size: 192 },
    { name: 'android-chrome-192x192.png', size: 192 },
    { name: 'favicon-512x512.png', size: 512 },
    { name: 'android-chrome-512x512.png', size: 512 },
  ];

  const svgBuffer = Buffer.from(svgContent);

  const pngBuffers = {};

  for (const { name, size } of sizes) {
    const buf = await sharp(svgBuffer).resize(size, size).png().toBuffer();
    fs.writeFileSync(path.join(publicDir, name), buf);
    pngBuffers[size] = buf;
    console.log(`Generated ${name} (${size}x${size})`);
  }

  // Generate OG image (1200x630)
  const ogBuffer = await sharp(Buffer.from(ogSvgContent))
    .resize(1200, 630)
    .png()
    .toBuffer();
  fs.writeFileSync(path.join(publicDir, 'govlyx-og.png'), ogBuffer);
  console.log('Generated govlyx-og.png (1200x630)');

  // Also sync to dist if dist exists
  const distDir = path.resolve('dist');
  if (fs.existsSync(distDir)) {
    fs.writeFileSync(path.join(distDir, 'govlyx-og.png'), ogBuffer);
    fs.writeFileSync(path.join(distDir, 'logo.svg'), svgContent);
    fs.writeFileSync(path.join(distDir, 'govlyx.svg'), svgContent);
    fs.writeFileSync(path.join(distDir, 'favicon.svg'), svgContent);
  }

  // Create a valid multi-size ICO file (16x16, 32x32, 48x48)
  const icoSizes = [16, 32, 48];
  const icoHeader = Buffer.alloc(6);
  icoHeader.writeUInt16LE(0, 0); // reserved
  icoHeader.writeUInt16LE(1, 2); // ICO image type
  icoHeader.writeUInt16LE(icoSizes.length, 4); // count of images

  const dirEntrySize = 16;
  let offset = 6 + dirEntrySize * icoSizes.length;
  const dirEntries = [];
  const imageBuffers = [];

  for (const size of icoSizes) {
    const pngData = pngBuffers[size];
    imageBuffers.push(pngData);

    const entry = Buffer.alloc(dirEntrySize);
    entry.writeUInt8(size === 256 ? 0 : size, 0); // width
    entry.writeUInt8(size === 256 ? 0 : size, 1); // height
    entry.writeUInt8(0, 2); // color palette (0 = no palette)
    entry.writeUInt8(0, 3); // reserved
    entry.writeUInt16LE(1, 4); // color planes
    entry.writeUInt16LE(32, 6); // bits per pixel
    entry.writeUInt32LE(pngData.length, 8); // image size in bytes
    entry.writeUInt32LE(offset, 12); // image data offset

    dirEntries.push(entry);
    offset += pngData.length;
  }

  const icoBuffer = Buffer.concat([icoHeader, ...dirEntries, ...imageBuffers]);
  fs.writeFileSync(path.join(publicDir, 'favicon.ico'), icoBuffer);
  if (fs.existsSync(distDir)) {
    fs.writeFileSync(path.join(distDir, 'favicon.ico'), icoBuffer);
  }
  console.log('Generated favicon.ico with 16x16, 32x32, 48x48 icons');
}

generateFavicons().catch(console.error);
