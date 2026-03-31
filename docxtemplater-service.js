// Microservicio para procesar DOCX con reemplazo XML + inserción de firma
const express = require('express');
const multer = require('multer');
const fs = require('fs');
const PizZip = require('pizzip');
const path = require('path');

const app = express();
const upload = multer({ storage: multer.memoryStorage() });
const enableSignatureImage = process.env.DOCXTEMPLATER_ENABLE_SIGNATURE_IMAGE !== 'false';
const forceUppercaseValues = process.env.DOCXTEMPLATER_FORCE_UPPERCASE !== 'false';

function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildPlaceholderAcrossTagsPattern(placeholder) {
    return Array.from(placeholder)
        .map((ch) => escapeRegExp(ch))
        .join('(?:<[^>]+>)*');
}

function replaceMarkerInXml(xmlContent, placeholder, replacement) {
    let result = xmlContent;
    let replacements = 0;

    const exactRegex = new RegExp(escapeRegExp(placeholder), 'g');
    const exactMatches = result.match(exactRegex);
    if (exactMatches) {
        replacements += exactMatches.length;
        result = result.replace(exactRegex, replacement);
    }

    return { xml: result, count: replacements };
}

app.post('/render-docx', upload.fields([{ name: 'files', maxCount: 1 }, { name: 'data', maxCount: 1 }]), (req, res) => {
    console.log('\n=== Nueva petición /render-docx ===');
    console.log('Files:', req.files ? Object.keys(req.files) : 'NO FILES');
    console.log('Body:', req.body ? Object.keys(req.body) : 'NO BODY');

    try {
        const fileField = req.files['files'] || req.files['file'] || req.files['template'];
        if (!fileField || !fileField[0]) {
            console.error('❌ Archivo DOCX no recibido. Keys:', req.files ? Object.keys(req.files) : 'none');
            throw new Error('Archivo DOCX no recibido.');
        }
        const file = fileField[0];
        console.log('✓ Archivo recibido:', file.originalname, '-', file.buffer.length, 'bytes');

        let data = {};
        let firmaImageBuffer = null;
        let firmaImageExt = 'jpeg';
        let firmaContentType = 'image/jpeg';

        if (req.body['data']) {
            try {
                data = JSON.parse(req.body['data']);
                console.log('✓ JSON parseado - Keys:', Object.keys(data));

                // Extraer firma si existe
                const firmaKey = ['firma.jpeg', 'firma.jpg', 'firma'].find((k) => data[k] && data[k].length > 100);
                if (firmaKey) {
                    let base64Data = String(data[firmaKey]);

                    if (base64Data.startsWith('data:')) {
                        const m = base64Data.match(/^data:([^;]+);base64,(.*)$/i);
                        if (m) {
                            firmaContentType = m[1].toLowerCase();
                            base64Data = m[2] || '';
                        }
                    } else if (base64Data.includes('base64,')) {
                        base64Data = base64Data.split('base64,')[1];
                    }

                    if (firmaContentType.includes('png')) {
                        firmaImageExt = 'png';
                        firmaContentType = 'image/png';
                    } else {
                        firmaImageExt = 'jpeg';
                        firmaContentType = 'image/jpeg';
                    }

                    firmaImageBuffer = Buffer.from(base64Data, 'base64');
                    console.log('✓ Firma extraída:', firmaImageBuffer.length, 'bytes', '|', firmaContentType);
                    delete data['firma.jpeg'];
                    delete data['firma.jpg'];
                    delete data['firma'];
                }
            } catch (e) {
                console.error('❌ Error JSON:', e.message);
                console.error('JSON raw:', req.body['data']?.substring(0, 200));
                throw new Error('Error JSON: ' + e.message);
            }
        } else {
            console.warn('⚠ No se recibió campo data en body');
        }

        console.log('⚙️ Procesando DOCX...');
        const zip = new PizZip(file.buffer);
        console.log('✓ ZIP creado');

        // ============================================
        // PASO 1: Reemplazo de TEXTO en todos los XML
        // ============================================
        console.log('📝 Reemplazando marcadores de TEXTO...');
        
        const xmlFiles = zip.file(/\.xml$/);
        xmlFiles.forEach((file) => {
            let xmlContent = file.asText();
            let modified = false;
            
            for (const [key, value] of Object.entries(data)) {
                const placeholder = '{{' + key + '}}';
                const replacementValue = forceUppercaseValues ? String(value).toUpperCase() : String(value);
                const replacement = replaceMarkerInXml(xmlContent, placeholder, replacementValue);
                if (replacement.count > 0) {
                    xmlContent = replacement.xml;
                    console.log(`  ✓ {{${key}}} -> ${replacement.count} veces`);
                    modified = true;
                }
            }
            
            if (modified) {
                zip.file(file.name, xmlContent);
            }
        });
        
        console.log('✓ Reemplazo de TEXTO completado');

        // ============================================
        // PASO 2: Insertar FIRMA digital en el documento
        // ============================================
        if (firmaImageBuffer && enableSignatureImage) {
            console.log('🖼️ Insertando FIRMA digital...');
            
            try {
                // 2.1: Agregar imagen al ZIP en media/ respetando el tipo real
                const imgName = `firma.${firmaImageExt}`;
                zip.file('word/media/' + imgName, firmaImageBuffer);
                console.log('  ✓ Imagen agregada a word/media/' + imgName, '-', firmaImageBuffer.length, 'bytes');

                // 2.2: Actualizar [Content_Types].xml
                let contentTypesXml = zip.file('[Content_Types].xml').asText();
                const extensionEntry = `<Default Extension="${firmaImageExt}" ContentType="${firmaContentType}"/>`;
                if (!contentTypesXml.includes(extensionEntry)) {
                    contentTypesXml = contentTypesXml.replace('</Types>', `${extensionEntry}</Types>`);
                    zip.file('[Content_Types].xml', contentTypesXml);
                    console.log('  ✓ Content-Type actualizado:', extensionEntry);
                }
                
                // 2.3: Crear/actualizar word/_rels/document.xml.rels
                let relsXml = '';
                let imageRelId = 'rId1';
                
                try {
                    relsXml = zip.file('word/_rels/document.xml.rels').asText();
                    console.log('  ✓ Rels existente encontrado');
                } catch (e) {
                    // Crear nuevo archivo rels
                    relsXml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>';
                    console.log('  ✓ Creando nuevo Rels');
                }
                
                // Contar relaciones existentes para generar nuevo rId
                const existingRels = relsXml.match(/Id="rId(\d+)"/g);
                let nextId = 1;
                if (existingRels) {
                    const maxId = Math.max(...existingRels.map(r => parseInt(r.match(/rId(\d+)/)[1])));
                    nextId = maxId + 1;
                }
                imageRelId = 'rId' + nextId;
                
                // Agregar relación de la imagen
                const newRel = `<Relationship Id="${imageRelId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${imgName}"/>`;
                relsXml = relsXml.replace('</Relationships>', newRel + '</Relationships>');
                zip.file('word/_rels/document.xml.rels', relsXml);
                console.log('  ✓ Relación agregada:', imageRelId);
                
                // 2.4: Reemplazar marcador de firma con imagen inline
                const firmaPlaceholders = [
                    '{{firma.jpeg}}',
                    '{{firma.jpg}}',
                    '{{firma}}',
                    '{{Firma.jpeg}}',
                    '{{Firma.jpg}}',
                    '{{Firma}}',
                    '{{FIRMA.JPEG}}',
                    '{{FIRMA.JPG}}',
                    '{{FIRMA}}'
                ];

                // Reemplazar el <w:p> completo que contiene el marcador por un párrafo con la imagen inline.
                // Esto evita XML inválido al operar dentro de un <w:r>.
                const firmaCx = 1524000; // ~160 px (EMUs)
                const firmaCy = 469900;  // ~49 px (EMUs)
                const buildFirmaParaXml = (drawingId) =>
                    `<w:p><w:pPr><w:jc w:val="left"/></w:pPr>` +
                    `<w:r><w:drawing>` +
                    `<wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">` +
                    `<wp:extent cx="${firmaCx}" cy="${firmaCy}"/>` +
                    `<wp:docPr id="${drawingId}" name="firma" descr="firma"/>` +
                    `<wp:cNvGraphicFramePr/>` +
                    `<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">` +
                    `<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">` +
                    `<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">` +
                    `<pic:nvPicPr><pic:cNvPr id="${drawingId}" name="firma"/><pic:cNvPicPr/></pic:nvPicPr>` +
                    `<pic:blipFill>` +
                    `<a:blip r:embed="${imageRelId}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>` +
                    `<a:stretch><a:fillRect/></a:stretch>` +
                    `</pic:blipFill>` +
                    `<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${firmaCx}" cy="${firmaCy}"/></a:xfrm>` +
                    `<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>` +
                    `</pic:pic></a:graphicData></a:graphic>` +
                    `</wp:inline></w:drawing></w:r></w:p>`;

                let firmaInsertada = 0;
                const documentFile = zip.file('word/document.xml');
                if (documentFile) {
                    let xml = documentFile.asText();
                    const docPrIds = Array.from(xml.matchAll(/<wp:docPr[^>]*\sid="(\d+)"/g)).map((m) => parseInt(m[1], 10)).filter((n) => !Number.isNaN(n));
                    const nextDrawingId = (docPrIds.length ? Math.max(...docPrIds) : 0) + 1;

                    // Reemplazar <w:p> que contenga cualquier variante del marcador de firma.
                    // IMPORTANTE: usar lookahead negativo (?!<w:p[\s>]) para no cruzar límites de párrafo
                    // y evitar consumir todos los párrafos anteriores al marcador.
                    for (const placeholder of firmaPlaceholders) {
                        const escapedPH = placeholder.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&');
                        // (?:(?!<w:p[\s>])[\s\S])*? -> avanza carácter a carácter sin cruzar <w:p>
                        const noCrossPara = `(?:(?!<w:p[\\s>])[\\s\\S])*?`;
                        const pRegex = new RegExp(`<w:p(?:\\s[^>]*)?>(?:(?!<w:p[\\s>])[\\s\\S])*?${escapedPH}${noCrossPara}</w:p>`, 'i');
                        if (pRegex.test(xml)) {
                            xml = xml.replace(pRegex, buildFirmaParaXml(nextDrawingId + firmaInsertada));
                            firmaInsertada += 1;
                            console.log(`  ✓ Párrafo con "${placeholder}" reemplazado por imagen`);
                        }
                    }

                    if (firmaInsertada > 0) {
                        zip.file('word/document.xml', xml);
                        console.log('  ✓ Firma insertada en word/document.xml');
                    } else {
                        console.log('  ⚠ No se encontró párrafo con marcador de firma en word/document.xml');
                    }
                }

                if (firmaInsertada === 0) {
                    console.log('  ⚠ No se pudo insertar firma en word/document.xml');
                }

                console.log('✓ Proceso de firma completado');
                
            } catch (imgErr) {
                console.error('  ❌ Error insertando firma:', imgErr.message);
                console.error('  Stack:', imgErr.stack);
                // Continuar sin la firma, el documento será válido
            }
        } else {
            console.log('⚠ Inserción de firma deshabilitada o sin firma, omitiendo imagen');
        }

        // Generar buffer de salida
        const buf = zip.generate({ type: 'nodebuffer' });
        console.log('✓ DOCX generado:', buf.length, 'bytes');

        res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
        res.set('Content-Disposition', 'attachment; filename="output.docx"');
        return res.send(buf);

    } catch (err) {
        console.error('❌ Error general:', err.message);
        console.error('Stack:', err.stack);
        res.status(500).json({ error: err.message, stack: err.stack });
    }
});

const PORT = Number(process.env.PORT || 3001);
app.listen(PORT, () => {
    console.log(`Docxtemplater service listening on port ${PORT}`);
    console.log('DOCXTEMPLATER_ENABLE_SIGNATURE_IMAGE =', enableSignatureImage);
    console.log('DOCXTEMPLATER_FORCE_UPPERCASE =', forceUppercaseValues);
});
