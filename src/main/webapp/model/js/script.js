// Close the popup
document.getElementById('location-popup').style.display = 'none';

// Sample building list (you can replace this with your own data)
var buildingList = [
  { name: 'Edificio 1', type: 'departments' },
  { name: 'Edificio 2', type: 'departments' },
  { name: 'Edificio 3', type: 'departments' },
  { name: 'Edificio 4', type: 'departments' },
  { name: 'Edificio 5(Auditório)', type: 'departments' },
  { name: 'Edificio 6', type: 'departments' },
  { name: 'Edificio 7', type: 'departments' },
  { name: 'Edificio 8', type: 'departments' },
  { name: 'Edificio 9', type: 'departments' },
  { name: 'Edificio 10', type: 'departments' },
  { name: 'Edificio 11', type: 'departments' },
  { name: 'Edificio Departamental', type: 'departments'},
  { name: 'Hangar 1', type: 'departments' },
  { name: 'Hangar 2', type: 'departments' },
  { name: 'Hangar 3', type: 'departments' },
  { name: 'Uninova', type: 'departments' },
  { name: 'Cemop', type: 'departments' },
  { name: 'Cenimat', type: 'departments' },
  { name: 'Biblioteca', type: 'student spaces' },
  { name: 'Papelaria Solução', type: 'student spaces' },
  { name: 'ViaCópia', type: 'student spaces' },
  { name: 'My Spot', type: 'restaurants' },
  { name: 'A Tia', type: 'restaurants' },
  { name: 'Cantina', type: 'restaurants' },
  { name: 'c@mpus.come', type: 'restaurants' },
  { name: 'Casa do Pessoal', type: 'restaurants' },
  { name: 'Tanto Faz-Bar Académico', type: 'restaurants' },
  { name: 'Bar da Biblioteca', type: 'restaurants' },
  { name: 'Lidl', type: 'markets' },
  { name: 'Mininova', type: 'markets' },
];

// Display the building list initially
var buttonsContainer = document.getElementById('buttons');
buildingList.forEach(function (building) {
  var button = document.createElement('button');
  button.setAttribute('data-type', building.type);
  button.textContent = building.name;
  buttonsContainer.appendChild(button);
});

// Get all checkboxes
var checkboxes = document.querySelectorAll('#poi-popup input[type="checkbox"]');


// Get all buttons
var buttons = document.querySelectorAll('#buttons button');

// Add event listener to each checkbox
checkboxes.forEach(function (checkbox) {
  checkbox.addEventListener('change', filterBuildings);
});

// Filtering function
function filterBuildings() {
  // Get the selected filters
  var selectedFilters = [];
  checkboxes.forEach(function (checkbox) {
    if (checkbox.checked) {
      selectedFilters.push(checkbox.value);
    }
  });

  // Filter the building list buttons and update visibility
  buttons.forEach(function (button) {
    var buildingType = button.getAttribute('data-type');
    if (!selectedFilters.length || selectedFilters.includes(buildingType)) {
      button.style.display = 'block';
    } else {
      button.style.display = 'none';
    }
  });
}

// Initially show all buttons
buttons.forEach(function (button) {
  button.style.display = 'block';
});
