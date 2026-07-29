const sourceType = document.querySelector('#sourceType');
const content = document.querySelector('#content');
const runButton = document.querySelector('#run');
const clearButton = document.querySelector('#clear');
const status = document.querySelector('#status');
const results = document.querySelector('#results');
const resultTitle = document.querySelector('#resultTitle');
const warnings = document.querySelector('#warnings');
const rows = document.querySelector('#attributeRows');

function showStatus(message, isError = true) {
  status.textContent = message;
  status.hidden = !message;
  status.classList.toggle('info', !isError);
}

function clearResults() {
  results.hidden = true;
  rows.replaceChildren();
  warnings.hidden = true;
  warnings.replaceChildren();
}

function renderResult(result) {
  clearResults();
  resultTitle.textContent = `Physical Models: ${result.count}`;
  result.attributes.forEach((attribute, index) => {
    const row = document.createElement('tr');
    const number = document.createElement('td');
    const path = document.createElement('td');
    number.textContent = String(index + 1);
    path.textContent = attribute.path;
    row.append(number, path);
    rows.append(row);
  });
  if (result.warnings.length) {
    const list = document.createElement('ul');
    result.warnings.forEach((warning) => {
      const item = document.createElement('li');
      item.textContent = warning;
      list.append(item);
    });
    warnings.append(list);
    warnings.hidden = false;
  }
  results.hidden = false;
}

runButton.addEventListener('click', async () => {
  showStatus('Running…', false);
  clearResults();
  runButton.disabled = true;
  try {
    const response = await fetch('/api/physical-models/parse', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({sourceType: sourceType.value, content: content.value})
    });
    const body = await response.json();
    if (!response.ok) {
      throw new Error(body.message || 'The parser request failed.');
    }
    renderResult(body);
    showStatus('', false);
  } catch (error) {
    showStatus(error.message);
  } finally {
    runButton.disabled = false;
  }
});

clearButton.addEventListener('click', () => {
  content.value = '';
  showStatus('');
  clearResults();
  content.focus();
});
