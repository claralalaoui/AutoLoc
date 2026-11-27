'use strict';

/* =========================================================================
 * AutoLoc web interface
 * Calls the REST API under /api with HTTP Basic credentials.
 * ========================================================================= */

const LABELS = {
    statutVehicule: {
        DISPONIBLE: ['Disponible', 'b-green'],
        LOUE: ['Loué', 'b-blue'],
        MAINTENANCE: ['Maintenance', 'b-amber'],
    },
    categorie: { CITADINE: 'Citadine', BERLINE: 'Berline', SUV: 'SUV', UTILITAIRE: 'Utilitaire' },
    statutReservation: {
        EN_ATTENTE: ['En attente', 'b-amber'],
        CONFIRMEE: ['Confirmée', 'b-blue'],
        TERMINEE: ['Terminée', 'b-green'],
        ANNULEE: ['Annulée', 'b-grey'],
    },
    modePaiement: { CARTE: 'Carte', ESPECES: 'Espèces', VIREMENT: 'Virement' },
};

const PAGES = {
    dashboard: 'Tableau de bord',
    vehicules: 'Véhicules',
    agences: 'Agences',
    clients: 'Clients',
    reservations: 'Réservations',
    contrats: 'Contrats',
    louer: 'Louer une voiture',
    'mes-reservations': 'Mes réservations',
    profils: 'Mes profils',
};

/** Pages shown in the menu for each kind of account (the first one is the home page). */
const NAV = {
    admin: ['dashboard', 'vehicules', 'agences', 'clients', 'reservations', 'contrats'],
    client: ['louer', 'mes-reservations', 'profils'],
};

const STORAGE_KEY = 'autoloc.auth';

const state = {
    auth: null,
    user: null,
    isAdmin: false,
    page: 'dashboard',
    data: {},
    filters: {},
    redraw: null,
};

/* ---------- Helpers ---------- */

const $ = (selector) => document.querySelector(selector);

function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) => (
        { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
}

const euroFormat = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });
const money = (v) => (v == null ? '—' : euroFormat.format(Number(v)));

function formatDate(iso) {
    if (!iso) return '—';
    const [y, m, d] = String(iso).slice(0, 10).split('-');
    return `${d}/${m}/${y}`;
}

function todayIso() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().slice(0, 10);
}

function daysBetween(start, end) {
    const ms = new Date(end + 'T00:00:00') - new Date(start + 'T00:00:00');
    return Math.round(ms / 86400000) + 1;
}

function badge(map, key) {
    const [label, cls] = map[key] || [key, 'b-grey'];
    return `<span class="badge ${cls}">${esc(label)}</span>`;
}

function options(map, selected) {
    return Object.entries(map).map(([value, label]) => ({
        value,
        label: Array.isArray(label) ? label[0] : label,
        selected: value === selected,
    }));
}

function normalize(text) {
    return String(text ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
}

function matches(item, query, keys) {
    if (!query) return true;
    const q = normalize(query);
    return keys.some((k) => normalize(item[k]).includes(q));
}

function toast(message, type = '') {
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.textContent = message;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), 4000);
}

/* ---------- API ---------- */

class ApiError extends Error {
    constructor(status, message, details = []) {
        super(message);
        this.status = status;
        this.details = details;
    }
}

async function api(method, path, body) {
    const headers = {
        Authorization: 'Basic ' + state.auth,
        'X-Requested-With': 'XMLHttpRequest',
        Accept: 'application/json',
    };
    const isFile = body instanceof FormData;   // the browser sets the multipart boundary itself
    if (body !== undefined && !isFile) headers['Content-Type'] = 'application/json';

    const response = await fetch('/api' + path, {
        method,
        headers,
        body: body === undefined ? undefined : (isFile ? body : JSON.stringify(body)),
    });

    if (response.status === 401) {
        throw new ApiError(401, 'Identifiant ou mot de passe incorrect.');
    }
    if (response.status === 403) {
        throw new ApiError(403, "Action réservée à l'administrateur.");
    }
    if (response.status === 204) return null;

    const text = await response.text();
    let data = null;
    try {
        data = text ? JSON.parse(text) : null;
    } catch {
        data = null;
    }
    if (!response.ok) {
        throw new ApiError(response.status, data?.message || `Erreur ${response.status}`, data?.details || []);
    }
    return data;
}

/* ---------- Authentication ---------- */

function encodeCredentials(username, password) {
    const bytes = new TextEncoder().encode(`${username}:${password}`);
    let binary = '';
    bytes.forEach((b) => { binary += String.fromCharCode(b); });
    return btoa(binary);
}

async function startSession(auth) {
    state.auth = auth;
    const me = await api('GET', '/me');
    state.user = me;
    state.isAdmin = me.roles.includes('ROLE_ADMIN');
    sessionStorage.setItem(STORAGE_KEY, auth);

    $('#user-name').textContent = me.username;
    $('#user-role').textContent = state.isAdmin ? 'Agence' : 'Client';
    $('#client-banner').hidden = state.isAdmin;
    document.body.classList.toggle('theme-client', !state.isAdmin);
    $('#nav').innerHTML = allowedPages()
        .map((page) => `<a href="#${page}" data-page="${page}">${esc(PAGES[page])}</a>`)
        .join('');
    $('#login-view').hidden = true;
    $('#app-view').hidden = false;
    route();
}

function logout(message) {
    state.auth = null;
    state.user = null;
    state.data = {};
    state.filters = {};
    state.redraw = null;
    $('#content').innerHTML = '';
    sessionStorage.removeItem(STORAGE_KEY);
    document.body.classList.remove('theme-client');
    closeModal();
    $('#app-view').hidden = true;
    $('#login-view').hidden = false;
    showLoginTab('login');
    const form = $('#login-form');
    form.password.value = '';
    if (!message) form.username.value = '';
    const error = $('#login-error');
    error.hidden = !message;
    error.textContent = message || '';
}

$('#login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('button[type=submit]');
    button.disabled = true;
    try {
        await startSession(encodeCredentials(form.username.value.trim(), form.password.value));
    } catch (error) {
        logout(error.status === 401 ? 'Identifiant ou mot de passe incorrect.' : 'Serveur injoignable. L\'application est-elle lancée ?');
    } finally {
        button.disabled = false;
    }
});

$('#logout').addEventListener('click', () => logout());

const DEMO_ACCOUNTS = { student: 'student123', admin: 'admin123' };

/* ---------- Sign-up ---------- */

const PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

function showLoginTab(tab) {
    document.querySelectorAll('.login-tab').forEach((button) => {
        const on = button.dataset.tab === tab;
        button.classList.toggle('on', on);
        button.setAttribute('aria-selected', String(on));
    });
    $('#login-form').hidden = tab !== 'login';
    $('#register-form').hidden = tab !== 'register';
    const first = (tab === 'login' ? $('#login-form') : $('#register-form')).querySelector('input');
    if (first) first.focus();
}

document.querySelectorAll('.login-tab').forEach((button) =>
    button.addEventListener('click', () => showLoginTab(button.dataset.tab)));

function setRegisterError(name, message) {
    const form = $('#register-form');
    const slot = form.querySelector(`[data-error-for="${name}"]`);
    if (!slot) return false;
    slot.hidden = !message;
    slot.textContent = message || '';
    form.querySelector(`[data-field="${name}"]`).classList.toggle('invalid', Boolean(message));
    return true;
}

$('#register-form').addEventListener('input', (event) => setRegisterError(event.target.name, ''));

$('#register-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const value = (name) => form.elements[name].value.trim();
    const email = value('email');
    const motDePasse = form.motDePasse.value;
    const errorBox = $('#register-error');
    errorBox.hidden = true;

    const errors = {
        prenom: value('prenom') ? '' : 'Le prénom est obligatoire.',
        nom: value('nom') ? '' : 'Le nom est obligatoire.',
        email: !email ? "L'email est obligatoire." : (EMAIL_RE.test(email) ? '' : "Format d'email invalide."),
        telephone: !value('telephone') || TELEPHONE_RE.test(value('telephone')) ? '' : 'Numéro de téléphone invalide.',
        numPermis: !value('numPermis') ? 'Le numéro de permis est obligatoire.'
            : (PERMIS_RE.test(value('numPermis')) ? '' : 'Le numéro de permis doit contenir 8 à 15 lettres ou chiffres, sans caractère spécial.'),
        motDePasse: PASSWORD_RE.test(motDePasse) ? '' : 'Le mot de passe doit contenir au moins 8 caractères, dont une lettre et un chiffre.',
        confirmation: form.confirmation.value === motDePasse ? '' : 'Les deux mots de passe ne sont pas identiques.',
    };
    Object.entries(errors).forEach(([name, message]) => setRegisterError(name, message));
    const firstError = Object.keys(errors).find((name) => errors[name]);
    if (firstError) {
        form.elements[firstError].focus();
        return;
    }

    const button = form.querySelector('button[type=submit]');
    button.disabled = true;
    try {
        // Public call: no Authorization header (an empty one would be rejected by Spring Security).
        const response = await fetch('/api/public/inscription', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' },
            body: JSON.stringify({
                email,
                motDePasse,
                prenom: value('prenom'),
                nom: value('nom'),
                telephone: value('telephone') || null,
                numPermis: value('numPermis'),
            }),
        });
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const others = (data?.details || []).filter((detail) => {
                const [field, ...rest] = detail.split(' : ');
                return !setRegisterError(field, rest.join(' : '));
            });
            const message = [data?.details?.length && !others.length ? '' : (data?.message || `Erreur ${response.status}`), ...others]
                .filter(Boolean).join(' ');
            errorBox.hidden = !message;
            errorBox.textContent = message;
            return;
        }
        form.reset();
        await startSession(encodeCredentials(data.identifiant, motDePasse));
        toast('Compte créé. Bienvenue !', 'success');
    } catch {
        errorBox.hidden = false;
        errorBox.textContent = "Serveur injoignable. L'application est-elle lancée ?";
    } finally {
        button.disabled = false;
    }
});
document.querySelectorAll('[data-demo]').forEach((button) => button.addEventListener('click', () => {
    const form = $('#login-form');
    form.username.value = button.dataset.demo;
    form.password.value = DEMO_ACCOUNTS[button.dataset.demo];
    form.querySelector('button[type=submit]').focus();
}));

/* ---------- Routing ---------- */

window.addEventListener('hashchange', route);

function allowedPages() {
    return state.isAdmin ? NAV.admin : NAV.client;
}

async function route() {
    if (!state.auth) return;
    const page = location.hash.slice(1);
    const allowed = allowedPages();
    state.page = allowed.includes(page) ? page : allowed[0];
    document.querySelectorAll('#nav a').forEach((a) => a.classList.toggle('active', a.dataset.page === state.page));
    $('#page-title').textContent = PAGES[state.page];
    $('#content').innerHTML = '<div class="card empty">Chargement…</div>';
    await refresh();
}

async function refresh() {
    const content = $('#content');
    try {
        await VIEWS[state.page]();
    } catch (error) {
        if (error.status === 401) {
            logout('Session expirée, reconnectez-vous.');
            return;
        }
        content.innerHTML = `<div class="card empty">Impossible de charger les données : ${esc(error.message)}</div>`;
    }
}

/* ---------- Generic UI pieces ---------- */

function table(headers, rows, emptyText) {
    if (!rows.length) return `<div class="empty">${esc(emptyText)}</div>`;
    const head = headers.map((h) => `<th class="${h.num ? 'num' : ''}">${esc(h.label)}</th>`).join('');
    return `<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`;
}

function actionButton(action, id, label, style = 'btn-ghost') {
    return `<button class="btn btn-sm ${style}" type="button" data-action="${action}" data-id="${esc(id)}">${esc(label)}</button>`;
}

function searchInput(placeholder) {
    const value = state.filters[state.page]?.q ?? '';
    return `<input class="input search" type="search" placeholder="${esc(placeholder)}" data-filter="q" value="${esc(value)}">`;
}

function filterSelect(name, allLabel, map) {
    const current = state.filters[state.page]?.[name] ?? '';
    const opts = Object.entries(map).map(([value, label]) => {
        const text = Array.isArray(label) ? label[0] : label;
        return `<option value="${esc(value)}" ${value === current ? 'selected' : ''}>${esc(text)}</option>`;
    }).join('');
    return `<select class="input" data-filter="${name}"><option value="">${esc(allLabel)}</option>${opts}</select>`;
}

function filters() {
    return state.filters[state.page] || {};
}

/** Renders a page with a card that contains a toolbar and a table zone redrawn on filter changes. */
function listLayout(title, toolbarHtml, drawTable) {
    $('#content').innerHTML = `
        <section class="card">
            <div class="card-head">
                <h2>${esc(title)}</h2>
                <div class="toolbar">${toolbarHtml}</div>
            </div>
            <div id="table-zone"></div>
        </section>`;
    state.redraw = () => { $('#table-zone').innerHTML = drawTable(); };
    state.redraw();
}

$('#content').addEventListener('input', (event) => {
    const name = event.target.dataset.filter;
    if (!name) return;
    state.filters[state.page] = { ...filters(), [name]: event.target.value };
    if (state.redraw) state.redraw();
});

$('#content').addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const handler = ACTIONS[button.dataset.action];
    if (handler) handler(button.dataset.id);
});

/* ---------- Modal, forms, confirmations ---------- */

function openModal(title, html) {
    $('#modal-title').textContent = title;
    $('#modal-body').innerHTML = html;
    $('#modal').hidden = false;
    const first = $('#modal-body').querySelector('input, select, button');
    if (first) first.focus();
}

function closeModal() {
    $('#modal').hidden = true;
    $('#modal-body').innerHTML = '';
}

$('#modal').addEventListener('click', (event) => {
    if (event.target.closest('[data-close]')) closeModal();
});
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !$('#modal').hidden) closeModal();
});

function fieldHtml(f, value) {
    const required = f.required ? 'required' : '';
    const cls = f.full ? 'field full' : 'field';
    const error = `<small class="field-error" data-error-for="${f.name}" hidden></small>`;
    if (f.type === 'select') {
        const empty = f.emptyLabel !== undefined ? `<option value="">${esc(f.emptyLabel)}</option>` : '';
        const opts = f.options.map((o) => {
            const selected = String(o.value) === String(value ?? '') ? 'selected' : '';
            return `<option value="${esc(o.value)}" ${selected}>${esc(o.label)}</option>`;
        }).join('');
        return `<label class="${cls}" data-field="${f.name}"><span>${esc(f.label)}</span><select name="${f.name}" ${required}>${empty}${opts}</select>${error}</label>`;
    }
    const extra = [
        f.step ? `step="${f.step}"` : '',
        f.min !== undefined ? `min="${f.min}"` : '',
        f.max !== undefined ? `max="${f.max}"` : '',
        f.maxlength ? `maxlength="${f.maxlength}"` : '',
        f.placeholder ? `placeholder="${esc(f.placeholder)}"` : '',
        f.autocomplete ? `autocomplete="${f.autocomplete}"` : '',
    ].join(' ');
    const heading = f.section ? `<h3 class="form-section full">${esc(f.section)}</h3>` : '';
    return `${heading}<label class="${cls}" data-field="${f.name}"><span>${esc(f.label)}</span>
        <input name="${f.name}" type="${f.type || 'text'}" value="${esc(value ?? '')}" ${required} ${extra}>${error}</label>`;
}

/**
 * Opens a form in the modal.
 * fields: [{ name, label, type, required, options, parse: 'number' | 'id', full,
 *            validate(value, data) -> message, showIf(data) -> boolean, section }]
 * Fields hidden by showIf are neither validated nor sent.
 */
function openForm({ title, fields, values = {}, submitLabel = 'Enregistrer', onSubmit, onChange, note, after }) {
    const html = `
        <form id="entity-form" novalidate>
            <div class="form-grid">${fields.map((f) => fieldHtml(f, values[f.name])).join('')}</div>
            <p id="form-note" class="form-note" ${note ? '' : 'hidden'} style="margin-top:14px">${esc(note || '')}</p>
            <p id="form-error" class="form-error" hidden style="margin-top:14px"></p>
            <div class="form-actions">
                <button class="btn btn-ghost" type="button" data-close>Annuler</button>
                <button class="btn btn-primary" type="submit">${esc(submitLabel)}</button>
            </div>
        </form>`;
    openModal(title, html);

    const form = $('#entity-form');
    const rawData = () => Object.fromEntries(fields.map((f) => [f.name, form.elements[f.name].value.trim()]));
    const visible = (f, raw) => !f.showIf || f.showIf(raw);

    const read = () => {
        const raw = rawData();
        const data = {};
        fields.filter((f) => visible(f, raw)).forEach((f) => {
            const value = raw[f.name];
            if (f.parse === 'number' || f.parse === 'id') data[f.name] = value === '' ? null : Number(value);
            else data[f.name] = value === '' ? null : value;
        });
        return data;
    };
    const showNote = (text) => {
        const el = $('#form-note');
        el.hidden = !text;
        el.textContent = text || '';
    };
    const setError = (name, message) => {
        const slot = form.querySelector(`[data-error-for="${name}"]`);
        const label = form.querySelector(`[data-field="${name}"]`);
        if (!slot || !label) return false;
        slot.hidden = !message;
        slot.textContent = message || '';
        label.classList.toggle('invalid', Boolean(message));
        return true;
    };
    const applyVisibility = () => {
        const raw = rawData();
        fields.forEach((f) => {
            const label = form.querySelector(`[data-field="${f.name}"]`);
            const show = visible(f, raw);
            label.hidden = !show;
            const heading = label.previousElementSibling;
            if (heading && heading.classList.contains('form-section')) heading.hidden = !show;
        });
    };
    const validate = () => {
        const raw = rawData();
        let first = null;
        fields.filter((f) => visible(f, raw)).forEach((f) => {
            const value = raw[f.name];
            let message = '';
            if (f.required && value === '') message = 'Ce champ est obligatoire.';
            else if (value !== '' && f.validate) message = f.validate(value, raw) || '';
            setError(f.name, message);
            if (message && !first) first = form.elements[f.name];
        });
        if (first) first.focus();
        return !first;
    };

    applyVisibility();
    form.addEventListener('input', (event) => {
        if (event.target.name) setError(event.target.name, '');
        applyVisibility();
        if (onChange) showNote(onChange(read()));
    });
    if (onChange) showNote(onChange(read()));

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const errorBox = $('#form-error');
        errorBox.hidden = true;
        if (!validate()) return;
        const button = form.querySelector('button[type=submit]');
        button.disabled = true;
        try {
            await onSubmit(read());
            closeModal();
            await refresh();
            if (after) after();
        } catch (error) {
            // Field errors returned by the server ("nouveauClient.email : ...") go under their field.
            const others = (error.details || []).filter((detail) => {
                const [path, ...rest] = detail.split(' : ');
                return !setError(path.split('.').pop(), rest.join(' : '));
            });
            const placedAll = error.details?.length && !others.length;
            errorBox.hidden = placedAll;
            errorBox.innerHTML = esc(placedAll ? '' : error.message) + (others.length ? '<br>' + others.map(esc).join('<br>') : '');
        } finally {
            button.disabled = false;
        }
    });
}

function confirmAction(title, message, confirmLabel, run) {
    openModal(title, `
        <p style="margin:0 0 6px">${esc(message)}</p>
        <p id="form-error" class="form-error" hidden style="margin-top:14px"></p>
        <div class="form-actions">
            <button class="btn btn-ghost" type="button" data-close>Annuler</button>
            <button class="btn btn-primary" type="button" id="confirm-btn">${esc(confirmLabel)}</button>
        </div>`);
    const button = $('#confirm-btn');
    button.addEventListener('click', async () => {
        button.disabled = true;
        try {
            const done = await run();
            closeModal();
            if (done) toast(done, 'success');
            await refresh();
        } catch (error) {
            const box = $('#form-error');
            box.hidden = false;
            box.textContent = error.message;
            button.disabled = false;
        }
    });
}

/* =========================================================================
 * Views
 * ========================================================================= */

const VIEWS = {};

/* ---------- Dashboard ---------- */

VIEWS.dashboard = async () => {
    const [d, reservations, contrats] = await Promise.all([
        api('GET', '/dashboard'), api('GET', '/reservations'), api('GET', '/contrats'),
    ]);
    state.redraw = null;

    const total = d.vehicules || 1;
    const part = (n) => `${(n / total) * 100}%`;
    const today = todayIso();
    const upcoming = reservations
        .filter((r) => (r.statut === 'EN_ATTENTE' || r.statut === 'CONFIRMEE') && r.dateFin >= today)
        .sort((a, b) => a.dateDebut.localeCompare(b.dateDebut))
        .slice(0, 6);
    const unpaid = contrats.filter((c) => Number(c.resteAPayer) > 0).slice(0, 6);

    const kpi = (label, value, sub = '') => `
        <div class="card kpi">
            <div class="kpi-label">${esc(label)}</div>
            <div class="kpi-value">${esc(value)}</div>
            ${sub ? `<div class="kpi-sub">${esc(sub)}</div>` : ''}
        </div>`;

    $('#content').innerHTML = `
        <div class="kpis">
            ${kpi('Véhicules', d.vehicules, `${d.vehiculesDisponibles} disponibles`)}
            ${kpi('Agences', d.agences)}
            ${kpi('Clients', d.clients)}
            ${kpi('Réservations actives', d.reservationsActives, 'en attente ou confirmées')}
            ${kpi('Contrats', d.contrats)}
            ${kpi('Total encaissé', money(d.totalEncaisse))}
        </div>

        <section class="card">
            <div class="card-head"><h2>État de la flotte</h2><span class="muted">${d.vehicules} véhicule(s)</span></div>
            <div class="card-body">
                <div class="stack-bar">
                    <span style="width:${part(d.vehiculesDisponibles)};background:var(--green)"></span>
                    <span style="width:${part(d.vehiculesLoues)};background:var(--blue)"></span>
                    <span style="width:${part(d.vehiculesEnMaintenance)};background:#e8a33d"></span>
                </div>
                <div class="legend">
                    <span><i class="dot" style="background:var(--green)"></i>Disponibles : ${d.vehiculesDisponibles}</span>
                    <span><i class="dot" style="background:var(--blue)"></i>Loués : ${d.vehiculesLoues}</span>
                    <span><i class="dot" style="background:#e8a33d"></i>En maintenance : ${d.vehiculesEnMaintenance}</span>
                </div>
            </div>
        </section>

        <div class="grid-2">
            <section class="card">
                <div class="card-head"><h2>Prochaines réservations</h2><a href="#reservations">Tout voir</a></div>
                <div class="list">
                    ${upcoming.length ? upcoming.map((r) => `
                        <div class="list-item">
                            <div>
                                <div class="strong">${esc(r.clientNom)}</div>
                                <div class="muted">${esc(r.vehiculeLibelle)} · ${formatDate(r.dateDebut)} → ${formatDate(r.dateFin)}</div>
                            </div>
                            ${badge(LABELS.statutReservation, r.statut)}
                        </div>`).join('') : '<div class="empty">Aucune réservation à venir.</div>'}
                </div>
            </section>
            <section class="card">
                <div class="card-head"><h2>Contrats à encaisser</h2><a href="#contrats">Tout voir</a></div>
                <div class="list">
                    ${unpaid.length ? unpaid.map((c) => `
                        <div class="list-item">
                            <div>
                                <div class="strong">Contrat n°${c.id}</div>
                                <div class="muted">${esc(c.clientNom || 'Sans réservation liée')}</div>
                            </div>
                            <span class="badge b-amber">Reste ${money(c.resteAPayer)}</span>
                        </div>`).join('') : '<div class="empty">Tous les contrats sont soldés.</div>'}
                </div>
            </section>
        </div>`;
};

/* ---------- Véhicules ---------- */

VIEWS.vehicules = async () => {
    const [vehicules, agences] = await Promise.all([api('GET', '/vehicules'), api('GET', '/agences')]);
    state.data.vehicules = vehicules;
    state.data.agences = agences;

    const toolbar = `
        ${searchInput('Rechercher (immatriculation, marque…)')}
        ${filterSelect('statut', 'Tous les statuts', LABELS.statutVehicule)}
        ${state.isAdmin ? '<button class="btn btn-primary" type="button" data-action="vehicule-new">Ajouter un véhicule</button>' : ''}`;

    listLayout(`Flotte (${vehicules.length})`, toolbar, () => {
        const f = filters();
        const rows = vehicules
            .filter((v) => !f.statut || v.statut === f.statut)
            .filter((v) => matches(v, f.q, ['immatriculation', 'marque', 'modele', 'agenceNom']))
            .map((v) => `
                <tr>
                    <td>${v.photoUrl
                        ? `<img class="thumb" src="${esc(v.photoUrl)}" alt="" loading="lazy">`
                        : '<span class="thumb thumb-empty" title="Pas de photo"></span>'}</td>
                    <td class="strong">${esc(v.immatriculation)}</td>
                    <td>${esc(v.marque)} ${esc(v.modele)}</td>
                    <td>${esc(LABELS.categorie[v.categorie] || v.categorie)}</td>
                    <td class="num">${money(v.tarifJournalier)}</td>
                    <td>${badge(LABELS.statutVehicule, v.statut)}</td>
                    <td>${v.agenceNom ? esc(v.agenceNom) : '<span class="muted">Non affecté</span>'}</td>
                    <td>${state.isAdmin ? `<div class="actions">
                        ${actionButton('vehicule-photo', v.id, 'Photo')}
                        ${actionButton('vehicule-edit', v.id, 'Modifier')}
                        ${actionButton('vehicule-delete', v.id, 'Supprimer', 'btn-danger')}</div>` : ''}</td>
                </tr>`);
        return table([
            { label: 'Photo' }, { label: 'Immatriculation' }, { label: 'Modèle' }, { label: 'Catégorie' },
            { label: 'Tarif / jour', num: true }, { label: 'Statut' }, { label: 'Agence' }, { label: '' },
        ], rows, 'Aucun véhicule ne correspond.');
    });
};

function vehiculeFields() {
    return [
        { name: 'immatriculation', label: 'Immatriculation', required: true, maxlength: 20 },
        { name: 'marque', label: 'Marque', required: true, maxlength: 50 },
        { name: 'modele', label: 'Modèle', required: true, maxlength: 50 },
        { name: 'categorie', label: 'Catégorie', type: 'select', required: true, options: options(LABELS.categorie) },
        { name: 'tarifJournalier', label: 'Tarif journalier (€)', type: 'number', step: '0.01', min: '0.01', required: true, parse: 'number' },
        { name: 'statut', label: 'Statut', type: 'select', required: true, options: options(LABELS.statutVehicule) },
        {
            name: 'agenceId', label: 'Agence', type: 'select', parse: 'id', full: true, emptyLabel: 'Aucune agence',
            options: (state.data.agences || []).map((a) => ({ value: a.id, label: `${a.nom} (${a.ville})` })),
        },
    ];
}

const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const PHOTO_MAX = 5 * 1024 * 1024;

function openPhotoForm(v) {
    openModal(`Photo de ${v.marque} ${v.modele}`, `
        <div class="photo-box">
            <div class="photo-preview" id="photo-preview">
                ${v.photoUrl ? `<img src="${esc(v.photoUrl)}" alt="Photo actuelle">` : '<span class="muted">Pas encore de photo</span>'}
            </div>
            <label class="field">
                <span>Choisir une image : JPEG, PNG ou WebP, 5 Mo maximum. Format paysage conseillé.</span>
                <input type="file" id="photo-input" accept="image/jpeg,image/png,image/webp">
            </label>
            <p id="form-error" class="form-error" hidden></p>
            <div class="form-actions">
                ${v.photoUrl ? '<button class="btn btn-danger" type="button" id="photo-delete">Supprimer la photo</button><span style="flex:1"></span>' : ''}
                <button class="btn btn-ghost" type="button" data-close>Annuler</button>
                <button class="btn btn-primary" type="button" id="photo-send" disabled>Enregistrer la photo</button>
            </div>
        </div>`);

    const input = $('#photo-input');
    const send = $('#photo-send');
    const errorBox = $('#form-error');
    const showError = (message) => { errorBox.hidden = !message; errorBox.textContent = message || ''; };
    let file = null;

    input.addEventListener('change', () => {
        file = input.files[0] || null;
        send.disabled = true;
        showError('');
        if (!file) return;
        if (!PHOTO_TYPES.includes(file.type)) { showError('Format non accepté : choisissez une image JPEG, PNG ou WebP.'); file = null; return; }
        if (file.size > PHOTO_MAX) { showError('La photo dépasse 5 Mo.'); file = null; return; }
        $('#photo-preview').innerHTML = `<img src="${URL.createObjectURL(file)}" alt="Aperçu">`;
        send.disabled = false;
    });

    send.addEventListener('click', async () => {
        if (!file) return;
        send.disabled = true;
        try {
            const data = new FormData();
            data.append('photo', file);
            await api('POST', `/vehicules/${v.id}/photo`, data);
            closeModal();
            toast('Photo enregistrée.', 'success');
            await refresh();
        } catch (error) {
            showError(error.message);
            send.disabled = false;
        }
    });

    const remove = $('#photo-delete');
    if (remove) {
        remove.addEventListener('click', async () => {
            remove.disabled = true;
            try {
                await api('DELETE', `/vehicules/${v.id}/photo`);
                closeModal();
                toast('Photo supprimée.', 'success');
                await refresh();
            } catch (error) {
                showError(error.message);
                remove.disabled = false;
            }
        });
    }
}

/* ---------- Agences ---------- */

VIEWS.agences = async () => {
    const agences = await api('GET', '/agences');
    state.data.agences = agences;
    const toolbar = `
        ${searchInput('Rechercher (nom, ville…)')}
        ${state.isAdmin ? '<button class="btn btn-primary" type="button" data-action="agence-new">Ajouter une agence</button>' : ''}`;

    listLayout(`Agences (${agences.length})`, toolbar, () => {
        const rows = agences
            .filter((a) => matches(a, filters().q, ['nom', 'ville', 'adresse']))
            .map((a) => `
                <tr>
                    <td class="strong">${esc(a.nom)}</td>
                    <td>${esc(a.ville)}</td>
                    <td>${esc(a.adresse)}</td>
                    <td>${esc(a.telephone || '—')}</td>
                    <td class="num">${a.nombreVehicules}</td>
                    <td>${state.isAdmin ? `<div class="actions">
                        ${actionButton('agence-edit', a.id, 'Modifier')}
                        ${actionButton('agence-delete', a.id, 'Supprimer', 'btn-danger')}</div>` : ''}</td>
                </tr>`);
        return table([
            { label: 'Nom' }, { label: 'Ville' }, { label: 'Adresse' }, { label: 'Téléphone' },
            { label: 'Véhicules', num: true }, { label: '' },
        ], rows, 'Aucune agence pour le moment.');
    });
};

const AGENCE_FIELDS = [
    { name: 'nom', label: 'Nom', required: true, maxlength: 100 },
    { name: 'ville', label: 'Ville', required: true, maxlength: 50 },
    { name: 'adresse', label: 'Adresse', required: true, full: true, maxlength: 255 },
    { name: 'telephone', label: 'Téléphone', type: 'tel', maxlength: 20 },
];

/* ---------- Clients ---------- */

VIEWS.clients = async () => {
    const clients = await api('GET', '/clients');
    state.data.clients = clients;
    const toolbar = `
        ${searchInput('Rechercher (nom, email, permis…)')}
        ${state.isAdmin ? '<button class="btn btn-primary" type="button" data-action="client-new">Ajouter un client</button>' : ''}`;

    listLayout(`Clients (${clients.length})`, toolbar, () => {
        const rows = clients
            .filter((c) => matches(c, filters().q, ['nom', 'prenom', 'email', 'numPermis']))
            .map((c) => `
                <tr>
                    <td class="strong">${esc(c.prenom)} ${esc(c.nom)}</td>
                    <td>${esc(c.email)}</td>
                    <td>${esc(c.telephone || '—')}</td>
                    <td>${esc(c.numPermis)}</td>
                    <td>${formatDate(c.dateInscription)}</td>
                    <td>${state.isAdmin ? `<div class="actions">
                        ${actionButton('client-edit', c.id, 'Modifier')}
                        ${actionButton('client-delete', c.id, 'Supprimer', 'btn-danger')}</div>` : ''}</td>
                </tr>`);
        return table([
            { label: 'Client' }, { label: 'Email' }, { label: 'Téléphone' }, { label: 'Permis' },
            { label: 'Inscription' }, { label: '' },
        ], rows, 'Aucun client pour le moment.');
    });
};

/* Same rules as the server (ClientDto): the server checks again, the browser only gives faster feedback. */
const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PERMIS_RE = /^[A-Za-z0-9]{8,15}$/;
const TELEPHONE_RE = /^\+?[0-9 .()-]{6,20}$/;

const CLIENT_FIELDS = [
    { name: 'prenom', label: 'Prénom', required: true, maxlength: 50, autocomplete: 'given-name' },
    { name: 'nom', label: 'Nom', required: true, maxlength: 50, autocomplete: 'family-name' },
    {
        name: 'email', label: 'Email', type: 'email', required: true, full: true, maxlength: 100,
        placeholder: 'nom@domaine.fr', autocomplete: 'email',
        validate: (v) => (EMAIL_RE.test(v) ? '' : "Format d'email invalide."),
    },
    {
        name: 'telephone', label: 'Téléphone', type: 'tel', maxlength: 20, autocomplete: 'tel',
        validate: (v) => (TELEPHONE_RE.test(v) ? '' : 'Numéro de téléphone invalide.'),
    },
    {
        name: 'numPermis', label: 'N° de permis', required: true, maxlength: 15, placeholder: '8 à 15 lettres ou chiffres',
        validate: (v) => (PERMIS_RE.test(v) ? '' : 'Le numéro de permis doit contenir 8 à 15 lettres ou chiffres, sans caractère spécial.'),
    },
];

/* ---------- Réservations ---------- */

VIEWS.reservations = async () => {
    const reservations = await api('GET', '/reservations');
    state.data.reservations = reservations;
    const toolbar = `
        ${searchInput('Rechercher (client, véhicule…)')}
        ${filterSelect('statut', 'Tous les statuts', LABELS.statutReservation)}
        ${state.isAdmin ? '<button class="btn btn-primary" type="button" data-action="reservation-new">Nouvelle réservation</button>' : ''}`;

    listLayout(`Réservations (${reservations.length})`, toolbar, () => {
        const f = filters();
        const rows = reservations
            .filter((r) => !f.statut || r.statut === f.statut)
            .filter((r) => matches(r, f.q, ['clientNom', 'vehiculeLibelle']))
            .map((r) => `
                <tr>
                    <td class="muted">n°${r.id}</td>
                    <td class="strong">${esc(r.clientNom)}</td>
                    <td>${esc(r.vehiculeLibelle)}</td>
                    <td>${formatDate(r.dateDebut)} → ${formatDate(r.dateFin)}<div class="muted">${r.nombreJours} jour(s)</div></td>
                    <td class="num">${money(r.montantEstime)}</td>
                    <td>${badge(LABELS.statutReservation, r.statut)}</td>
                    <td><div class="actions">${reservationActions(r)}</div></td>
                </tr>`);
        return table([
            { label: 'N°' }, { label: 'Client' }, { label: 'Véhicule' }, { label: 'Période' },
            { label: 'Montant', num: true }, { label: 'Statut' }, { label: '' },
        ], rows, 'Aucune réservation ne correspond.');
    });
};

function reservationActions(r) {
    const buttons = [];
    if (r.contratId) buttons.push(`<a class="btn btn-sm btn-ghost" href="#contrats">Contrat n°${r.contratId}</a>`);
    if (!state.isAdmin) return buttons.join('');
    if (r.statut === 'EN_ATTENTE') {
        buttons.push(actionButton('reservation-confirm', r.id, 'Confirmer', 'btn-primary'));
        buttons.push(actionButton('reservation-cancel', r.id, 'Annuler'));
    }
    if (r.statut === 'CONFIRMEE') {
        buttons.push(actionButton('reservation-finish', r.id, 'Terminer'));
        buttons.push(actionButton('reservation-cancel', r.id, 'Annuler'));
    }
    if ((r.statut === 'CONFIRMEE' || r.statut === 'TERMINEE') && !r.contratId) {
        buttons.push(actionButton('reservation-contract', r.id, 'Créer le contrat', 'btn-primary'));
    }
    if ((r.statut === 'EN_ATTENTE' || r.statut === 'ANNULEE') && !r.contratId) {
        buttons.push(actionButton('reservation-delete', r.id, 'Supprimer', 'btn-danger'));
    }
    return buttons.join('');
}

async function openReservationForm() {
    const [clients, vehicules] = await Promise.all([api('GET', '/clients'), api('GET', '/vehicules')]);
    const reservables = vehicules.filter((v) => v.statut !== 'MAINTENANCE');
    if (!clients.length || !reservables.length) {
        toast('Il faut au moins un client et un véhicule hors maintenance pour créer une réservation.', 'error');
        return;
    }
    const tarifs = Object.fromEntries(vehicules.map((v) => [v.id, Number(v.tarifJournalier)]));
    const today = todayIso();

    openForm({
        title: 'Nouvelle réservation',
        submitLabel: 'Créer la réservation',
        values: { dateDebut: today, dateFin: today },
        fields: [
            {
                name: 'clientId', label: 'Client', type: 'select', required: true, parse: 'id', full: true, emptyLabel: 'Choisir un client',
                options: clients.map((c) => ({ value: c.id, label: `${c.prenom} ${c.nom} (${c.email})` })),
            },
            {
                name: 'vehiculeId', label: 'Véhicule', type: 'select', required: true, parse: 'id', full: true, emptyLabel: 'Choisir un véhicule',
                options: reservables.map((v) => ({
                    value: v.id,
                    label: `${v.marque} ${v.modele} · ${v.immatriculation} · ${money(v.tarifJournalier)}/jour`,
                })),
            },
            { name: 'dateDebut', label: 'Début', type: 'date', required: true },
            { name: 'dateFin', label: 'Fin', type: 'date', required: true },
        ],
        onChange: (data) => {
            if (!data.vehiculeId || !data.dateDebut || !data.dateFin) return '';
            if (data.dateFin < data.dateDebut) return 'La date de fin doit être après la date de début.';
            const jours = daysBetween(data.dateDebut, data.dateFin);
            return `Estimation : ${jours} jour(s) × ${money(tarifs[data.vehiculeId])} = ${money(jours * tarifs[data.vehiculeId])}`;
        },
        onSubmit: async (data) => {
            await api('POST', '/reservations', data);
            toast('Réservation créée.', 'success');
        },
    });
}

function changeStatut(id, statut, title, message, label) {
    confirmAction(title, message, label, async () => {
        await api('PATCH', `/reservations/${id}/statut`, { statut });
        return 'Réservation mise à jour.';
    });
}

/* ---------- Contrats ---------- */

VIEWS.contrats = async () => {
    const contrats = await api('GET', '/contrats');
    state.data.contrats = contrats;
    state.redraw = null;

    if (!contrats.length) {
        $('#content').innerHTML = `<div class="card empty">Aucun contrat. Confirmez une réservation puis cliquez sur « Créer le contrat ».</div>`;
        return;
    }

    $('#content').innerHTML = `<div class="contracts">${contrats.map((c) => {
        const paid = Number(c.totalPaye);
        const total = Number(c.montantTotal) || 1;
        const percent = Math.min(100, (paid / total) * 100);
        const reste = Number(c.resteAPayer);
        return `
        <article class="card contract">
            <div class="card-head">
                <h2>Contrat n°${c.id}</h2>
                ${c.valide ? '<span class="badge b-green">Valide</span>' : '<span class="badge b-grey">Invalide</span>'}
            </div>
            <div class="card-body">
                <div class="row"><span class="muted">Client</span><span class="strong">${esc(c.clientNom || '—')}</span></div>
                <div class="row"><span class="muted">Véhicule</span><span>${esc(c.vehiculeLibelle || '—')}</span></div>
                <div class="row"><span class="muted">Signé le</span><span>${formatDate(c.dateSignature)}</span></div>
                <div class="row"><span class="muted">Montant total</span><span class="strong">${money(c.montantTotal)}</span></div>
                <div>
                    <div class="row" style="margin-bottom:6px">
                        <span class="muted">Payé ${money(c.totalPaye)}</span>
                        <span class="${reste > 0 ? '' : 'muted'}">${reste > 0 ? `Reste ${money(reste)}` : 'Soldé'}</span>
                    </div>
                    <div class="progress"><span style="width:${percent}%"></span></div>
                </div>
                <ul class="pay-list">
                    ${c.paiements.length ? c.paiements.map((p) => `
                        <li><span>${formatDate(p.datePaiement)} · ${esc(LABELS.modePaiement[p.modePaiement] || p.modePaiement)}</span>
                            <span class="strong">${money(p.montant)}</span></li>`).join('')
                        : '<li class="muted">Aucun paiement</li>'}
                </ul>
                ${state.isAdmin && c.valide && reste > 0
                    ? `<div>${actionButton('contrat-pay', c.id, 'Ajouter un paiement', 'btn-primary')}</div>` : ''}
            </div>
        </article>`;
    }).join('')}</div>`;
};

/* ---------- Espace client : louer une voiture ---------- */

/* Side-view silhouettes, one per category (original drawings, the car faces right). */
const CAR_SHAPES = {
    BERLINE: {
        body: 'M12,70 Q12,58 28,55 L66,50 Q88,31 114,29 L152,29 Q174,31 192,49 L226,54 Q238,57 238,67 L238,74 Q238,78 234,78 L16,78 Q12,78 12,74 Z',
        window: 'M72,50 Q92,35 114,33 L150,33 Q168,35 182,49 Z',
        wheels: [62, 192], r: 14, light: [228, 58], tail: [12, 58],
    },
    CITADINE: {
        body: 'M28,70 Q28,58 42,54 L66,50 Q82,30 106,27 L150,27 Q170,28 182,44 L204,52 Q218,56 218,66 L218,74 Q218,78 214,78 L32,78 Q28,78 28,74 Z',
        window: 'M72,50 Q87,33 106,31 L148,31 Q164,32 174,48 Z',
        wheels: [70, 180], r: 14, light: [208, 57], tail: [28, 57],
    },
    SUV: {
        body: 'M12,68 Q12,52 30,48 L58,46 Q70,22 94,19 L172,19 Q190,20 198,40 L226,46 Q240,50 240,62 L240,74 Q240,80 234,80 L18,80 Q12,80 12,74 Z',
        window: 'M64,46 Q75,26 94,24 L170,24 Q183,25 190,44 Z',
        wheels: [62, 196], r: 16, light: [230, 52], tail: [12, 52],
    },
    UTILITAIRE: {
        body: 'M12,76 L12,28 Q12,16 26,16 L168,16 Q184,16 194,31 L214,52 Q232,56 232,66 L232,76 Q232,80 228,80 L16,80 Q12,80 12,76 Z',
        window: 'M152,22 L170,22 Q181,23 189,35 L201,50 L152,50 Z',
        wheels: [56, 188], r: 15, light: [222, 57], tail: [12, 40],
    },
};
let carUid = 0;

function carSvg(categorie, { beamLength = 70, viewBox = '-25 0 300 100', label = '' } = {}) {
    const c = CAR_SHAPES[categorie] || CAR_SHAPES.BERLINE;
    const id = `beam${carUid++}`;
    const [lx, ly] = c.light;
    const bx = lx + 9;
    const wheel = (x) => `
        <g class="wheel-spin">
            <circle class="tyre" cx="${x}" cy="78" r="${c.r}"/>
            <circle class="rim" cx="${x}" cy="78" r="${c.r * 0.55}"/>
            <line class="spoke" x1="${x - c.r * 0.55}" y1="78" x2="${x + c.r * 0.55}" y2="78"/>
            <line class="spoke" x1="${x}" y1="${78 - c.r * 0.55}" x2="${x}" y2="${78 + c.r * 0.55}"/>
        </g>`;
    return `
    <svg viewBox="${viewBox}" role="img" aria-label="${esc(label)}">
        <defs>
            <linearGradient id="${id}" x1="0" x2="1" y1="0" y2="0">
                <stop offset="0" stop-color="#dff8ff" stop-opacity=".75"/>
                <stop offset="1" stop-color="#3fd4ff" stop-opacity="0"/>
            </linearGradient>
        </defs>
        <polygon class="beam" fill="url(#${id})"
            points="${bx},${ly} ${bx + beamLength},${ly - 16} ${bx + beamLength},${ly + 26} ${bx},${ly + 4}"/>
        <ellipse cx="${(c.wheels[0] + c.wheels[1]) / 2}" cy="94" rx="${(c.wheels[1] - c.wheels[0]) / 2 + 30}" ry="4" fill="#000" opacity=".45"/>
        <path class="car-body" d="${c.body}"/>
        <path class="car-window" d="${c.window}"/>
        <rect class="headlight" x="${lx}" y="${ly}" width="9" height="4" rx="2"/>
        <rect class="taillight" x="${c.tail[0]}" y="${c.tail[1]}" width="4" height="6" rx="1.5"/>
        ${wheel(c.wheels[0])}${wheel(c.wheels[1])}
    </svg>`;
}

VIEWS.louer = async () => {
    const vehicules = await api('GET', '/vehicules');
    const catalogue = vehicules.filter((v) => v.statut !== 'MAINTENANCE');
    state.data.catalogue = catalogue;
    const current = filters().categorie || '';
    const switches = [['', 'Toutes'], ...Object.entries(LABELS.categorie)].map(([value, label]) => `
        <button class="switch ${value === current ? 'on' : ''}" type="button" data-action="filtre-categorie" data-id="${value}">
            <i></i>${esc(label)}</button>`).join('');

    $('#content').innerHTML = `
        <section class="hero">
            <div class="hero-text">
                <h2>La route vous attend.</h2>
                <p>Choisissez votre véhicule et vos dates. Vous voyez le prix tout de suite, l'agence confirme votre demande.</p>
            </div>
            <div class="hero-scene" aria-hidden="true">
                <div class="hero-car">${carSvg('BERLINE', { beamLength: 200, viewBox: '0 0 460 100' })}</div>
            </div>
            <div class="road" aria-hidden="true"></div>
        </section>
        <div class="catalogue-bar">
            <div class="switches">${switches}</div>
            ${searchInput('Marque, modèle ou agence')}
        </div>
        <div id="table-zone"></div>`;

    state.redraw = () => {
        const f = filters();
        const cars = catalogue
            .filter((v) => !f.categorie || v.categorie === f.categorie)
            .filter((v) => matches(v, f.q, ['marque', 'modele', 'agenceNom']));
        $('#table-zone').innerHTML = cars.length ? `<div class="catalogue">${cars.map((v) => `
            <article class="card car">
                ${v.photoUrl
                    ? `<div class="car-stage has-photo"><img class="car-photo" src="${esc(v.photoUrl)}" alt="${esc(v.marque)} ${esc(v.modele)}" loading="lazy"></div>`
                    : `<div class="car-stage">${carSvg(v.categorie, { label: `${v.marque} ${v.modele}` })}</div>`}
                <div class="card-body">
                    <div class="car-title">${esc(v.marque)} ${esc(v.modele)}</div>
                    <div class="car-meta">
                        <span>${esc(LABELS.categorie[v.categorie] || v.categorie)}, immatriculé ${esc(v.immatriculation)}</span>
                        ${v.agenceNom ? `<span>À retirer : ${esc(v.agenceNom)}</span>` : ''}
                    </div>
                    <div class="car-row">
                        <div class="car-price">${money(v.tarifJournalier)} <small>par jour</small></div>
                        ${v.statut === 'LOUE'
                            ? '<span class="status s-confirmee" title="Déjà loué, vous pouvez le réserver à d\'autres dates"><i></i>En location</span>'
                            : '<span class="status s-terminee"><i></i>Libre</span>'}
                    </div>
                </div>
                <div class="car-foot">${actionButton('louer', v.id, 'Louer ce véhicule', 'btn-primary btn-block')}</div>
            </article>`).join('')}</div>`
            : '<div class="card empty">Aucun véhicule ne correspond. Essayez une autre catégorie.</div>';
    };
    state.redraw();
};

/** Status shown as a small light, used in the customer space. */
function statusLight(statut) {
    const map = {
        EN_ATTENTE: ['s-attente', 'En attente de l\'agence'],
        CONFIRMEE: ['s-confirmee', 'Confirmée'],
        TERMINEE: ['s-terminee', 'Terminée'],
        ANNULEE: ['s-annulee', 'Annulée'],
    };
    const [cls, label] = map[statut] || ['s-annulee', statut];
    return `<span class="status ${cls}"><i></i>${esc(label)}</span>`;
}

VIEWS['mes-reservations'] = async () => {
    const reservations = await api('GET', '/me/reservations');
    state.data.mesReservations = reservations;
    state.redraw = null;

    if (!reservations.length) {
        $('#content').innerHTML = `<div class="card empty">Vous n'avez pas encore de réservation.
            <div style="margin-top:12px"><a class="btn btn-primary" href="#louer">Louer une voiture</a></div></div>`;
        return;
    }
    const rows = reservations.map((r) => `
        <tr>
            <td class="muted">n°${r.id}</td>
            <td class="strong">${esc(r.vehiculeLibelle)}<div class="muted">pour ${esc(r.clientNom)}</div></td>
            <td><span class="mine-period">du ${formatDate(r.dateDebut)} au ${formatDate(r.dateFin)}</span><div class="muted">${r.nombreJours} jour(s)</div></td>
            <td class="num">${money(r.montantEstime)}</td>
            <td>${statusLight(r.statut)}</td>
            <td><div class="actions">${r.statut === 'EN_ATTENTE'
                ? actionButton('ma-reservation-cancel', r.id, 'Annuler ma demande', 'btn-danger') : ''}</div></td>
        </tr>`);
    $('#content').innerHTML = `
        <section class="card">
            <div class="card-head"><h2>Mes réservations (${reservations.length})</h2>
                <a class="btn btn-primary" href="#louer">Nouvelle location</a></div>
            ${table([
                { label: 'N°' }, { label: 'Véhicule' }, { label: 'Période' },
                { label: 'Montant', num: true }, { label: 'Statut' }, { label: '' },
            ], rows, '')}
        </section>
        <p class="muted" style="margin:0">Tant que le voyant clignote, l'agence n'a pas encore validé votre demande. Une fois confirmée, le véhicule vous est réservé.</p>`;
};

VIEWS.profils = async () => {
    const profils = await api('GET', '/me/profils');
    state.data.profils = profils;
    state.redraw = null;
    const head = `<div class="catalogue-bar"><p class="muted" style="margin:0">Un profil par conducteur. Vous le choisissez au moment de louer.</p>
        ${actionButton('profil-new', '', 'Ajouter un profil', 'btn-primary')}</div>`;
    if (!profils.length) {
        $('#content').innerHTML = `${head}<div class="card empty">Aucun profil pour le moment. Vous pouvez aussi en créer un directement en louant une voiture.</div>`;
        return;
    }
    const line = (label, value) => `<div class="row"><span class="muted">${esc(label)}</span><span class="strong">${esc(value || '—')}</span></div>`;
    $('#content').innerHTML = `${head}<div class="profiles">${profils.map((p) => `
        <section class="card profile-card">
            <div class="card-head"><h2>${esc(p.prenom)} ${esc(p.nom)}</h2>
                <div class="actions">${actionButton('profil-edit', p.id, 'Modifier')}${actionButton('profil-delete', p.id, 'Supprimer', 'btn-danger')}</div></div>
            <div class="card-body" style="display:grid;gap:10px">
                ${line('Email', p.email)}
                ${line('Téléphone', p.telephone)}
                ${line('N° de permis', p.numPermis)}
                ${line('Créé le', formatDate(p.dateInscription))}
            </div>
        </section>`).join('')}</div>`;
};

const NEW_PROFILE = 'nouveau';

function openLocationForm(v, profils) {
    const today = todayIso();
    const tarif = Number(v.tarifJournalier);
    const isNew = (raw) => raw.clientId === NEW_PROFILE;
    const profileFields = CLIENT_FIELDS.map((f, i) => ({
        ...f,
        showIf: isNew,
        section: i === 0 ? 'Nouveau profil' : undefined,
    }));

    openForm({
        title: `Louer ${v.marque} ${v.modele}`,
        submitLabel: 'Envoyer ma demande',
        values: {
            dateDebut: today,
            dateFin: today,
            clientId: profils.length ? profils[0].id : NEW_PROFILE,
            email: state.user.username.includes('@') ? state.user.username : '',
        },
        fields: [
            {
                name: 'clientId', label: 'Conducteur', type: 'select', required: true, full: true,
                options: [
                    ...profils.map((p) => ({ value: p.id, label: `${p.prenom} ${p.nom} (${p.email})` })),
                    { value: NEW_PROFILE, label: 'Saisir un nouveau profil' },
                ],
            },
            ...profileFields,
            {
                name: 'dateDebut', label: 'Du', type: 'date', required: true, min: today, section: 'Dates',
                validate: (value) => (value < today ? 'La date de début ne peut pas être dans le passé.' : ''),
            },
            {
                name: 'dateFin', label: 'Au', type: 'date', required: true, min: today,
                validate: (value, raw) => (value < raw.dateDebut ? 'La date de fin doit être après la date de début.' : ''),
            },
        ],
        onChange: (data) => {
            if (!data.dateDebut || !data.dateFin || data.dateFin < data.dateDebut) return '';
            const jours = daysBetween(data.dateDebut, data.dateFin);
            return `${jours} jour(s) × ${money(tarif)} = ${money(jours * tarif)}`;
        },
        onSubmit: async (data) => {
            const { clientId, dateDebut, dateFin, ...profil } = data;
            const body = clientId === NEW_PROFILE
                ? { nouveauClient: profil, vehiculeId: v.id, dateDebut, dateFin }
                : { clientId: Number(clientId), vehiculeId: v.id, dateDebut, dateFin };
            await api('POST', '/me/reservations', body);
            toast("Demande envoyée. L'agence va la confirmer.", 'success');
        },
        after: () => { location.hash = '#mes-reservations'; },
    });
}

/* =========================================================================
 * Actions (buttons with data-action)
 * ========================================================================= */

const findIn = (key, id) => (state.data[key] || []).find((item) => String(item.id) === String(id));

const ACTIONS = {
    louer: async (id) => {
        const v = findIn('catalogue', id);
        try {
            openLocationForm(v, await api('GET', '/me/profils'));
        } catch (error) {
            toast(error.message, 'error');
        }
    },
    'profil-new': () => openForm({
        title: 'Ajouter un profil',
        fields: CLIENT_FIELDS,
        onSubmit: async (data) => { await api('POST', '/me/profils', data); toast('Profil ajouté.', 'success'); },
    }),
    'profil-edit': (id) => openForm({
        title: 'Modifier le profil',
        fields: CLIENT_FIELDS,
        values: findIn('profils', id),
        onSubmit: async (data) => { await api('PUT', `/me/profils/${id}`, data); toast('Profil modifié.', 'success'); },
    }),
    'profil-delete': (id) => {
        const p = findIn('profils', id);
        confirmAction('Supprimer le profil', `Supprimer le profil de ${p.prenom} ${p.nom} ?`, 'Supprimer',
            async () => { await api('DELETE', `/me/profils/${id}`); return 'Profil supprimé.'; });
    },
    'filtre-categorie': (value) => {
        state.filters[state.page] = { ...filters(), categorie: value };
        document.querySelectorAll('.switch').forEach((b) => b.classList.toggle('on', b.dataset.id === value));
        if (state.redraw) state.redraw();
    },
    'ma-reservation-cancel': (id) => confirmAction('Annuler ma demande', `Annuler la demande de réservation n°${id} ?`,
        'Annuler la demande', async () => {
            await api('POST', `/me/reservations/${id}/annulation`);
            return 'Demande annulée.';
        }),

    'vehicule-new': () => openForm({
        title: 'Ajouter un véhicule',
        fields: vehiculeFields(),
        values: { statut: 'DISPONIBLE', categorie: 'CITADINE' },
        onSubmit: async (data) => { await api('POST', '/vehicules', data); toast('Véhicule ajouté.', 'success'); },
    }),
    'vehicule-photo': (id) => openPhotoForm(findIn('vehicules', id)),
    'vehicule-edit': (id) => {
        const v = findIn('vehicules', id);
        openForm({
            title: `Modifier ${v.immatriculation}`,
            fields: vehiculeFields(),
            values: v,
            onSubmit: async (data) => { await api('PUT', `/vehicules/${id}`, data); toast('Véhicule modifié.', 'success'); },
        });
    },
    'vehicule-delete': (id) => {
        const v = findIn('vehicules', id);
        confirmAction('Supprimer le véhicule', `Supprimer ${v.marque} ${v.modele} (${v.immatriculation}) ?`, 'Supprimer',
            async () => { await api('DELETE', `/vehicules/${id}`); return 'Véhicule supprimé.'; });
    },

    'agence-new': () => openForm({
        title: 'Ajouter une agence',
        fields: AGENCE_FIELDS,
        onSubmit: async (data) => { await api('POST', '/agences', data); toast('Agence ajoutée.', 'success'); },
    }),
    'agence-edit': (id) => openForm({
        title: 'Modifier l\'agence',
        fields: AGENCE_FIELDS,
        values: findIn('agences', id),
        onSubmit: async (data) => { await api('PUT', `/agences/${id}`, data); toast('Agence modifiée.', 'success'); },
    }),
    'agence-delete': (id) => {
        const a = findIn('agences', id);
        confirmAction('Supprimer l\'agence', `Supprimer l'agence ${a.nom} (${a.ville}) ?`, 'Supprimer',
            async () => { await api('DELETE', `/agences/${id}`); return 'Agence supprimée.'; });
    },

    'client-new': () => openForm({
        title: 'Ajouter un client',
        fields: CLIENT_FIELDS,
        onSubmit: async (data) => { await api('POST', '/clients', data); toast('Client ajouté.', 'success'); },
    }),
    'client-edit': (id) => openForm({
        title: 'Modifier le client',
        fields: CLIENT_FIELDS,
        values: findIn('clients', id),
        onSubmit: async (data) => { await api('PUT', `/clients/${id}`, data); toast('Client modifié.', 'success'); },
    }),
    'client-delete': (id) => {
        const c = findIn('clients', id);
        confirmAction('Supprimer le client', `Supprimer ${c.prenom} ${c.nom} ?`, 'Supprimer',
            async () => { await api('DELETE', `/clients/${id}`); return 'Client supprimé.'; });
    },

    'reservation-new': () => openReservationForm().catch((error) => toast(error.message, 'error')),
    'reservation-confirm': (id) => changeStatut(id, 'CONFIRMEE', 'Confirmer la réservation',
        'Le véhicule passera au statut « Loué ».', 'Confirmer'),
    'reservation-finish': (id) => changeStatut(id, 'TERMINEE', 'Terminer la location',
        'Le véhicule redeviendra disponible.', 'Terminer'),
    'reservation-cancel': (id) => changeStatut(id, 'ANNULEE', 'Annuler la réservation',
        'Cette action est définitive.', 'Annuler la réservation'),
    'reservation-contract': (id) => {
        const r = findIn('reservations', id);
        confirmAction('Créer le contrat',
            `Créer le contrat de ${r.clientNom} pour ${r.nombreJours} jour(s), soit ${money(r.montantEstime)} ?`, 'Créer le contrat',
            async () => { await api('POST', `/reservations/${id}/contrat`); return 'Contrat créé.'; });
    },
    'reservation-delete': (id) => confirmAction('Supprimer la réservation', `Supprimer la réservation n°${id} ?`, 'Supprimer',
        async () => { await api('DELETE', `/reservations/${id}`); return 'Réservation supprimée.'; }),

    'contrat-pay': (id) => {
        const c = findIn('contrats', id);
        openForm({
            title: `Paiement · contrat n°${id}`,
            submitLabel: 'Enregistrer le paiement',
            note: `Reste à payer : ${money(c.resteAPayer)}`,
            values: { montant: c.resteAPayer, modePaiement: 'CARTE' },
            fields: [
                { name: 'montant', label: 'Montant (€)', type: 'number', step: '0.01', min: '0.01', max: c.resteAPayer, required: true, parse: 'number' },
                { name: 'modePaiement', label: 'Mode de paiement', type: 'select', required: true, options: options(LABELS.modePaiement) },
            ],
            onSubmit: async (data) => { await api('POST', `/contrats/${id}/paiements`, data); toast('Paiement enregistré.', 'success'); },
        });
    },
};

/* ---------- Start ---------- */

(async function init() {
    $('#login-car').innerHTML = carSvg('SUV', { beamLength: 230, viewBox: '0 0 490 100' });
    const saved = sessionStorage.getItem(STORAGE_KEY);
    if (!saved) {
        logout();
        return;
    }
    try {
        await startSession(saved);
    } catch {
        logout();
    }
})();
