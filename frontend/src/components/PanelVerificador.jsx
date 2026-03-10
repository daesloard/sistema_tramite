import { useCallback, useEffect, useMemo, useState } from 'react';
import { API_TRAMITES_URL } from '../config/api';
import { listarTramites } from '../services/api';
import { filtrarCertificadosGenerados } from '../utils/certificateFilters';
import { formatearFechaHora, obtenerTextoDiasHabilesRestantes } from '../utils/dateUtils';
import AvisoModal from './common/AvisoModal';

// Nuevos sub-componentes
import VerificadorHeader from './verificador/VerificadorHeader';
import VerificadorCertificates from './verificador/VerificadorCertificates';
import VerificadorTramiteList from './verificador/VerificadorTramiteList';
import VerificadorDetail from './verificador/VerificadorDetail';

import { getPanelVerificadorStyles } from '../styles/components/PanelVerificadorStyles';
const styles = getPanelVerificadorStyles();

const FILTROS_VERIFICADOR = [
    { id: 'pendientes', titulo: '📥 Pendientes', estados: ['RADICADO', 'EN_VALIDACION'] },
    { id: 'aprobados', titulo: '✅ Aprobados (Firma)', estados: ['EN_FIRMA'] },
    { id: 'negados', titulo: '❌ Rechazados', estados: ['RECHAZADO'] },
    { id: 'finalizados', titulo: '🎓 Finalizados', estados: ['FINALIZADO'] },
];

const ESTADO_BADGE = {
    RADICADO: { text: 'Nuevo', style: { background: '#ebf5ff', color: '#1e40af' } },
    EN_VALIDACION: { text: 'En Revisión', style: { background: '#fef3c7', color: '#92400e' } },
    EN_FIRMA: { text: 'Para Firma', style: { background: '#f5f3ff', color: '#5b21b6' } },
    FINALIZADO: { text: 'Terminado', style: { background: '#ecfdf5', color: '#065f46' } },
    RECHAZADO: { text: 'Rechazado', style: { background: '#fef2f2', color: '#991b1b' } },
};

const getEstadoBadge = (estado) => ESTADO_BADGE[estado] || { text: estado, style: { background: '#f3f4f6', color: '#374151' } };

export default function PanelVerificador({ usuarioActual }) {
    const [solicitudes, setSolicitudes] = useState([]);
    const [filtroActual, setFiltroActual] = useState(FILTROS_VERIFICADOR[0]);
    const [selectedSolicitud, setSelectedSolicitud] = useState(null);
    const [observaciones, setObservaciones] = useState('');
    const [consecutivo, setConsecutivo] = useState('');
    const [procesando, setProcesando] = useState(false);
    const [busquedaSolicitudes, setBusquedaSolicitudes] = useState('');
    const [aviso, setAviso] = useState(null);
    const [certificadosExpandido, setCertificadosExpandido] = useState(false);
    const [filtroCertRadicado, setFiltroCertRadicado] = useState('');
    const [filtroCertNombre, setFiltroCertNombre] = useState('');
    const [filtroCertTipo, setFiltroCertTipo] = useState('todos');
    const [cargandoConsolidado, setCargandoConsolidado] = useState(false);
    const [documentStatus, setDocumentStatus] = useState(null);
    const [loadingDocumentos, setLoadingDocumentos] = useState(false);
    const [enviandoNotificacionAdmin, setEnviandoNotificacionAdmin] = useState(false);
    const [width, setWidth] = useState(window.innerWidth);

    useEffect(() => {
        const handleResize = () => setWidth(window.innerWidth);
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    const isMobile = width < 768;

    const mostrarAviso = (tipo, mensaje) => setAviso({ tipo, mensaje });

    const fetchSolicitudes = useCallback(async ({ forceRefresh = false } = {}) => {
        // setLoading(true);
        try {
            const data = await listarTramites({ forceRefresh });
            setSolicitudes(data);
        } catch (err) {
            console.error(err);
            mostrarAviso('error', 'Error al cargar solicitudes');
        } finally {
            // setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (usuarioActual?.username) fetchSolicitudes();
    }, [usuarioActual?.username, fetchSolicitudes]);

    const solicitudesFiltradas = useMemo(() => 
        solicitudes.filter((s) => filtroActual.estados.includes(s.estado)), 
    [solicitudes, filtroActual]);

    const solicitudesFiltradasBusqueda = useMemo(() => {
        const term = busquedaSolicitudes.trim().toLowerCase();
        if (!term) return solicitudesFiltradas;
        return solicitudesFiltradas.filter((s) => {
            const campos = [s.numeroRadicado, s.nombreSolicitante, s.numeroDocumento, s.estado, s.tipoTramite];
            return campos.some(v => (v || '').toString().toLowerCase().includes(term));
        });
    }, [solicitudesFiltradas, busquedaSolicitudes]);

    const certificadosGeneradosFiltrados = useMemo(() => filtrarCertificadosGenerados(solicitudes, {
        radicado: filtroCertRadicado,
        nombre: filtroCertNombre,
        tipo: filtroCertTipo,
    }), [solicitudes, filtroCertRadicado, filtroCertNombre, filtroCertTipo]);

    const handleAprobar = async () => {
        if (!selectedSolicitud || procesando) return;
        setProcesando(true);
        try {
            const response = await fetch(`${API_TRAMITES_URL}/${selectedSolicitud.id}/verificar`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Username': usuarioActual.username },
                body: JSON.stringify({ aprobado: true, observaciones, consecutivo }),
            });
            if (!response.ok) throw new Error(await response.text() || 'Error al aprobar');
            mostrarAviso('success', 'Solicitud aprobada con éxito');
            setSelectedSolicitud(null);
            fetchSolicitudes({ forceRefresh: true });
        } catch (err) {
            mostrarAviso('error', err.message);
        } finally {
            setProcesando(false);
        }
    };

    const handleRechazar = async () => {
        if (!selectedSolicitud || procesando) return;
        if (!observaciones.trim()) return mostrarAviso('warning', 'Debes ingresar una observación para rechazar');
        setProcesando(true);
        try {
            const response = await fetch(`${API_TRAMITES_URL}/${selectedSolicitud.id}/verificar`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Username': usuarioActual.username },
                body: JSON.stringify({ aprobado: false, observaciones }),
            });
            if (!response.ok) throw new Error(await response.text() || 'Error al rechazar');
            mostrarAviso('success', 'Solicitud rechazada');
            setSelectedSolicitud(null);
            fetchSolicitudes({ forceRefresh: true });
        } catch (err) {
            mostrarAviso('error', err.message);
        } finally {
            setProcesando(false);
        }
    };

    const handleConsolidado = async () => {
        setCargandoConsolidado(true);
        try {
            const response = await fetch(`${API_TRAMITES_URL}/export/pdf`, {
                headers: { 'X-Username': usuarioActual.username },
            });
            if (!response.ok) throw new Error('Error al generar consolidado');
            const blob = await response.blob();
            window.open(URL.createObjectURL(blob), '_blank');
        } catch (err) {
            mostrarAviso('error', err.message);
        } finally {
            setCargandoConsolidado(false);
        }
    };

    const cargarEstadoDocumentos = async (tramiteId) => {
        setLoadingDocumentos(true);
        try {
            const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/verificar-documentos`, {
                headers: { 'X-Username': usuarioActual.username },
            });
            if (!response.ok) throw new Error('Error al cargar estado documental');
            setDocumentStatus(await response.json());
        } catch (err) {
            mostrarAviso('error', err.message);
        } finally {
            setLoadingDocumentos(false);
        }
    };

    const handleNotificarAdmin = async () => {
        if (!selectedSolicitud || enviandoNotificacionAdmin) return;
        setEnviandoNotificacionAdmin(true);
        try {
            const response = await fetch(`${API_TRAMITES_URL}/${selectedSolicitud.id}/notificar-admin`, {
                method: 'POST',
                headers: { 'X-Username': usuarioActual.username },
            });
            if (!response.ok) throw new Error(await response.text() || 'Error al notificar');
            mostrarAviso('success', 'Administrador notificado para revisión documental');
        } catch (err) {
            mostrarAviso('error', err.message);
        } finally {
            setEnviandoNotificacionAdmin(false);
        }
    };

    useEffect(() => {
        if (selectedSolicitud) {
            cargarEstadoDocumentos(selectedSolicitud.id);
        } else {
            setDocumentStatus(null);
        }
    }, [selectedSolicitud, cargarEstadoDocumentos]);

    const abrirDocumento = async (tramiteId, tipo) => {
        try {
            const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}?accion=ver`, {
                headers: { 'X-Username': usuarioActual.username },
            });
            if (!response.ok) throw new Error('Documento no disponible');
            window.open(URL.createObjectURL(await response.blob()), '_blank');
        } catch (err) {
            mostrarAviso('error', err.message);
        }
    };

    const descargarDocumento = async (tramiteId, tipo) => {
        try {
            const response = await fetch(`${API_TRAMITES_URL}/${tramiteId}/descargar/${tipo}?accion=descargar`, {
                headers: { 'X-Username': usuarioActual.username },
            });
            if (!response.ok) throw new Error('Error al descargar');
            const link = document.createElement('a');
            link.href = URL.createObjectURL(await response.blob());
            link.download = `${tipo}_${tramiteId}`;
            link.click();
        } catch (err) {
            mostrarAviso('error', err.message);
        }
    };

    const resolverClaveCertificado = (tipoCertificado) => {
        const t = (tipoCertificado || '').toLowerCase();
        if (t === 'electoral') return 'electoral';
        if (t === 'jac') return 'residencia';
        return 'sisben';
    };

    const obtenerDocumentosAdjuntos = useCallback((tramite) => {
        const clave = resolverClaveCertificado(tramite?.tipo_certificado);
        return [
            { key: 'identidad', label: 'Documento de Identidad' },
            { key: 'solicitud', label: 'Documento de Solicitud' },
            { key: clave, label: `Certificado (${(tramite?.tipo_certificado || clave).toUpperCase()})` },
        ];
    }, []);

    const selectedSolicitudFull = useMemo(() => {
        if (!selectedSolicitud) return null;
        const docs = obtenerDocumentosAdjuntos(selectedSolicitud);
        const faltantes = documentStatus ? docs.filter(d => !documentStatus[d.key]?.cargado).map(d => d.label) : [];
        const obligatoriosOk = documentStatus ? docs.every(d => !!documentStatus[d.key]?.cargado) : false;
        const totalMostrados = docs.filter(d => documentStatus?.[d.key]?.cargado).length;
        const desfase = documentStatus && documentStatus.totalDocumentosCargados > totalMostrados;
        
        return {
            ...selectedSolicitud,
            documentosAdjuntos: docs,
            documentosFaltantes: faltantes,
            documentosObligatoriosCompletos: obligatoriosOk,
            totalDocumentosMostrados: totalMostrados,
            hayDesfaseDocumentos: desfase,
            esPendiente: selectedSolicitud.estado === 'RADICADO' || selectedSolicitud.estado === 'EN_VALIDACION',
            textoDiasHabiles: obtenerTextoDiasHabilesRestantes(selectedSolicitud.fechaVencimiento)
        };
    }, [selectedSolicitud, documentStatus, obtenerDocumentosAdjuntos]);

    if (!usuarioActual?.username) return null;

    return (
        <main style={styles.contenedor}>
            <AvisoModal aviso={aviso} onClose={() => setAviso(null)} />

            <VerificadorHeader
                filtros={FILTROS_VERIFICADOR}
                filtroActual={filtroActual}
                setFiltroActual={setFiltroActual}
                handleConsolidado={handleConsolidado}
                cargandoConsolidado={cargandoConsolidado}
                isMobile={isMobile}
                styles={styles}
            />

            <VerificadorCertificates
                certificadosExpandido={certificadosExpandido}
                setCertificadosExpandido={setCertificadosExpandido}
                filtroCertRadicado={filtroCertRadicado}
                setFiltroCertRadicado={setFiltroCertRadicado}
                filtroCertNombre={filtroCertNombre}
                setFiltroCertNombre={setFiltroCertNombre}
                filtroCertTipo={filtroCertTipo}
                setFiltroCertTipo={setFiltroCertTipo}
                certificadosGeneradosFiltrados={certificadosGeneradosFiltrados}
                formatearFechaHora={formatearFechaHora}
                styles={styles}
            />

            <div style={{ ...styles.cuerpo, ...(isMobile ? {} : styles.cuerpoDesktop) }}>
                {(!isMobile || !selectedSolicitud) && (
                    <VerificadorTramiteList
                        filtroActual={filtroActual}
                        solicitudesFiltradasBusqueda={solicitudesFiltradasBusqueda}
                        solicitudesFiltradas={solicitudesFiltradas}
                        busquedaSolicitudes={busquedaSolicitudes}
                        setBusquedaSolicitudes={setBusquedaSolicitudes}
                        selectedSolicitud={selectedSolicitud}
                        setSelectedSolicitud={setSelectedSolicitud}
                        setConsecutivo={setConsecutivo}
                        setObservaciones={setObservaciones}
                        isMobile={isMobile}
                        estadoBadge={getEstadoBadge}
                        formatearFechaHora={formatearFechaHora}
                        obtenerTextoDiasHabilesRestantes={obtenerTextoDiasHabilesRestantes}
                        styles={styles}
                    />
                )}

                {selectedSolicitudFull && (
                    <VerificadorDetail
                        selectedSolicitud={selectedSolicitudFull}
                        setSelectedSolicitud={setSelectedSolicitud}
                        consecutivo={consecutivo}
                        setConsecutivo={setConsecutivo}
                        observaciones={observaciones}
                        setObservaciones={setObservaciones}
                        documentosAdjuntos={selectedSolicitudFull.documentosAdjuntos}
                        documentStatus={documentStatus}
                        loadingDocumentos={loadingDocumentos}
                        totalDocumentosMostrados={selectedSolicitudFull.totalDocumentosMostrados}
                        hayDesfaseDocumentos={selectedSolicitudFull.hayDesfaseDocumentos}
                        documentosFaltantes={selectedSolicitudFull.documentosFaltantes}
                        documentosObligatoriosCompletos={selectedSolicitudFull.documentosObligatoriosCompletos}
                        esPendienteSeleccionada={selectedSolicitudFull.esPendiente}
                        textoDiasHabilesSeleccionada={selectedSolicitudFull.textoDiasHabiles}
                        procesando={procesando}
                        enviandoNotificacionAdmin={enviandoNotificacionAdmin}
                        handleAprobar={handleAprobar}
                        handleRechazar={handleRechazar}
                        handleNotificarAdmin={handleNotificarAdmin}
                        abrirDocumento={abrirDocumento}
                        descargarDocumento={descargarDocumento}
                        formatearFechaHora={formatearFechaHora}
                        isMobile={isMobile}
                        styles={styles}
                    />
                )}
            </div>
        </main>
    );
}