import fetch from 'node-fetch';

const url = 'http://localhost:8080/api/tramites/upload/verificar/91';

async function test() {
    try {
        const response = await fetch(url, {
            headers: {
                'X-Username': 'verificador'
            }
        });
        console.log('Status:', response.status);
        const text = await response.text();
        console.log('Body:', text);
    } catch (err) {
        console.error('Fetch error:', err);
    }
}

test();
