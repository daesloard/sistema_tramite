# Docxtemplater Service

Microservicio Node.js para reemplazo de marcadores en archivos DOCX usando docxtemplater.

## Uso

1. Instala dependencias:
   ```bash
   npm install
   ```

2. Inicia el servicio:
   ```bash
   npm start
   ```

3. Endpoint disponible:
   - POST `/render-docx`
     - Form-data:
       - `template`: archivo DOCX plantilla
       - `data`: JSON string con los valores de los marcadores
     - Respuesta: archivo DOCX procesado

## Ejemplo de petición

```bash
curl -X POST http://localhost:3001/render-docx \
  -F "template=@plantilla.docx" \
  -F "data={\"nombreSolicitante\":\"Juan Pérez\",\"consecutivo\":\"123\"}"
```
