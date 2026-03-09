import { cp, mkdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const frontendRoot = path.resolve(__dirname, '..');
const distDir = path.join(frontendRoot, 'dist');
const backendStaticDir = path.resolve(frontendRoot, '..', 'backend', 'src', 'main', 'resources', 'static');

async function ensureDistExists() {
  try {
    const info = await stat(distDir);
    if (!info.isDirectory()) {
      throw new Error('dist no es un directorio. Ejecuta npm run build primero.');
    }
  } catch {
    throw new Error('No se encontró frontend/dist. Ejecuta npm run build primero.');
  }
}

async function publish() {
  await ensureDistExists();

  await rm(backendStaticDir, { recursive: true, force: true });
  await mkdir(backendStaticDir, { recursive: true });

  await cp(distDir, backendStaticDir, { recursive: true });

   
  console.log(`Frontend publicado en: ${backendStaticDir}`);
}

publish().catch((error) => {
   
  console.error('Error publicando frontend en backend:', error.message);
  process.exitCode = 1;
});
