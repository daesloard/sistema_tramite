const express = require('express');
const multer = require('multer');
const Docxtemplater = require('docxtemplater');
const PizZip = require('pizzip');
const fs = require('fs');

const app = express();
const upload = multer();

app.use(express.json());

// Endpoint para procesar plantilla DOCX
app.post('/render-docx', upload.any(), (req, res) => {
  try {
    const file = (req.files || []).find((f) => ['template', 'files', 'file'].includes(f.fieldname));
    if (!file || !file.buffer) {
      return res.status(400).json({ error: 'Archivo DOCX no recibido en campos template/files/file' });
    }
    const templateBuffer = file.buffer;
    const data = JSON.parse(req.body.data);
    console.log('Datos recibidos:', data);
    const zip = new PizZip(templateBuffer);
    const doc = new Docxtemplater(zip, {
      paragraphLoop: true,
      linebreaks: true,
      nullGetter: () => ''
    });
    doc.setData(data);
    try {
      doc.render();
      const markers = Object.keys(data);
      console.log('Marcadores detectados:', markers);
      const buf = doc.getZip().generate({ type: 'nodebuffer' });
      res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      res.send(buf);
    } catch (renderErr) {
      // Si faltan marcadores en data, reintentar rellenándolos con cadena vacía.
      if (renderErr.errors && renderErr.errors.length > 0) {
        const missingMarkers = [...new Set(renderErr.errors
          .filter(e => e.properties && e.properties.id === 'missingTag')
          .map(e => e.properties.tag)
          .filter(Boolean))];

        if (missingMarkers.length > 0) {
          console.warn('Marcadores faltantes detectados, se rellenan vacíos:', missingMarkers);
          const patchedData = { ...data };
          missingMarkers.forEach((tag) => {
            if (!(tag in patchedData)) patchedData[tag] = '';
          });

          const retryZip = new PizZip(templateBuffer);
          const retryDoc = new Docxtemplater(retryZip, {
            paragraphLoop: true,
            linebreaks: true,
            nullGetter: () => ''
          });
          retryDoc.setData(patchedData);
          retryDoc.render();

          const buf = retryDoc.getZip().generate({ type: 'nodebuffer' });
          res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
          res.send(buf);
          return;
        }
      }
      // Otros errores
      console.error('Error al renderizar plantilla:', renderErr);
      res.status(500).json({ error: renderErr.message, details: renderErr.errors });
    }
  } catch (err) {
    console.error('Error general:', err);
    res.status(500).json({ error: err.message });
  }
});

app.listen(3001, () => {
  console.log('Docxtemplater service running on port 3001');
});
