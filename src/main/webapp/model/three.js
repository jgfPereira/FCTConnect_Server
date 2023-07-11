//import * as THREE from 'https://unpkg.com/three@0.150.1/build/three.module.js';
import * as THREE from 'three';
import TWEEN from 'https://cdn.jsdelivr.net/npm/@tweenjs/tween.js@18.5.0/dist/tween.esm.js';
//import { GLTFLoader } from "https://unpkg.com/three@0.150.1/examples/js/loaders/GLTFLoader.js";
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RGBELoader } from 'three/addons/loaders/RGBELoader.js';
import { GUI } from 'three/addons/libs/lil-gui.module.min.js';
import { DRACOLoader } from 'three/addons/loaders/DRACOLoader.js';
//import { DRACOLoader } from "https://cdn.skypack.dev/three@0.125.0/examples/jsm/loaders/DRACOLoader";
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js';
import { initializeApp } from "https://www.gstatic.com/firebasejs/9.15.0/firebase-app.js"
import { getDatabase, ref, get, push, onValue, remove } from "https://www.gstatic.com/firebasejs/9.15.0/firebase-database.js"
import TouchControls from './js/TouchControls.js'
const appSettings = {
    databaseURL: "https://fctconnectdb-default-rtdb.europe-west1.firebasedatabase.app/"
}

let playerUsername;
var canvas, controls;
var camera, scene, renderer, labelRenderer, ambientLight;
var clock = new THREE.Clock();
var updatables = [];
var mixer, mixer2;
let player;
var model;
var laboratory;
var keyboard = {};
let latestUserPosition = new THREE.Vector3();
let gui, actions, face, activeAction, previousAction;
const api = { state: 'boxing' };
const pInit = { state: 'monster' };
let anim;
let gltfLoader;
let cPointLabel;
let signBoxGroup;
let secretDoorsGroup;
let eventArray = [];
let isCodeExecutionEnabled = true;
let isOpen = false;
let cube3;
let mapPointer;
let mapPointerTimeout;
let npc;
let myInventory= ['item1', 'item2'];
let friendModels = {}; 
const inventory = {
    item1: { src: 'book.jpg', alt: 'Item 1' },
    item2: { src: 'book.jpg', alt: 'Item 2' },
    item3: { src: 'book.jpg', alt: 'Item 3' }
};
// Define an array of positions for the player to loop through
const npcPositions = [
    new THREE.Vector3(10, 0, 0),   // Position 1
    new THREE.Vector3(0, 0, 10),   // Position 2
    new THREE.Vector3(-10, 0, 0),  // Position 3
    new THREE.Vector3(0, 0, -10)   // Position 4
  ];
  
let currentNpcPositionIndex = 0;  // Index of the current position
const rooms = {
    lab: {
        topX: -20,
        bottomX: -30,
        topZ: 31,
        bottomZ: 14,
        position: new THREE.Vector3(-25, 2, 20)
    },
    roofTop: {
        topX: 38,
        bottomX: -4,
        topZ: -167,
        bottomZ: -190,
        position: new THREE.Vector3(12, 12, -172)
    },
    // Add more objects as needed
  };

const buildingList = [
    { name: 'Edificio 1', type: 'departments', location: { x: 25, y: 0, z: 5 }, rotation: 1 },
    { name: 'Edificio 2', type: 'departments', location: { x: 174, y: 0, z: 16 }, rotation: 1.33 },
    { name: 'Edificio 3', type: 'departments', location: { x: -109, y: 0, z: -227 }, rotation: 2 },
    { name: 'Edificio 4', type: 'departments', location: { x: -124, y: 0, z: -180 }, rotation: 1 },
    { name: 'Edificio 5(Auditório)', type: 'departments', location: { x: -95, y: 0, z: -234 }, rotation: 1 },
    { name: 'Edificio 6', type: 'departments', location: { x: 235, y: 0, z: 49 }, rotation: 0.82 },
    { name: 'Edificio 7', type: 'departments', location: { x: -4, y: 0, z: 29 }, rotation: 1 },
    { name: 'Edificio 8', type: 'departments', location: { x: -75, y: 0, z: 33 }, rotation: 1 },
    { name: 'Edificio 9', type: 'departments', location: { x: -102, y: 0, z: 81 }, rotation: 2 },
    { name: 'Edificio 10', type: 'departments', location: { x: 76, y: 0, z: 30 }, rotation: 1 },
    { name: 'Edificio 11', type: 'departments', location: { x: -82, y: 0, z: -209 }, rotation: 2 },
    { name: 'Edificio Departamental', type: 'departments', location: { x: -144, y: 0, z: -116 }, rotation: 2 },
    { name: 'Hangar 1', type: 'departments', location: { x: -102, y: 0, z: -66 }, rotation: 0.45 },
    { name: 'Hangar 2', type: 'departments', location: { x: -86, y: 0, z: -83 }, rotation: 0.45 },
    { name: 'Hangar 3', type: 'departments', location: { x: -65, y: 0, z: -113 }, rotation: 0.45 },
    { name: 'Uninova', type: 'departments', location: { x: 160, y: 0, z: 89 }, rotation: 1.33 },
    { name: 'Cemop', type: 'departments', location: { x: 193, y: 0, z: 127 }, rotation: 1.33 },
    { name: 'Cenimat', type: 'departments', location: { x: 223, y: 0, z: 175 }, rotation: 1.33 },
    { name: 'Biblioteca', type: 'student spaces', location: { x: 42, y: 0, z: -163 }, rotation: 1 },
    { name: 'Papelaria Solução', type: 'student spaces', location: { x: 50, y: 0, z: -33 }, rotation: 1 },
    { name: 'ViaCópia', type: 'student spaces', location: { x: 117, y: 0, z: -63 }, rotation: 1 },
    { name: 'My Spot', type: 'restaurants', location: { x: 13, y: 0, z: 71 }, rotation: 1 },
    { name: 'A Tia', type: 'restaurants', location: { x: 88, y: 0, z: -21 }, rotation: 2 },
    { name: 'Cantina', type: 'restaurants', location: { x: 92, y: 0, z: -40 }, rotation: 1 },
    { name: 'c@mpus.come', type: 'restaurants', location: { x: 47, y: 0, z: -67 }, rotation: 1 },
    { name: 'Casa do Pessoal', type: 'restaurants', location: { x: 28, y: 0, z: -72 }, rotation: 2 },
    { name: 'Tanto Faz-Bar Académico', type: 'restaurants', location: { x: -82, y: 0, z: -53 }, rotation: 0.44 },
    { name: 'Bar da Biblioteca', type: 'restaurants', location: { x: 42, y: 0, z: -179 }, rotation: 2 },
    { name: 'Lidl', type: 'markets', location: { x: 193, y: 0, z: 264 }, rotation: 0.8 },
    { name: 'Mininova', type: 'markets', location: { x: 43, y: 0, z: -29 }, rotation: 2 }
];
init();



latestUserPosition.set(5, 0, 5);
/*
document.addEventListener('mousedown', (event) => {
  const mouse = new THREE.Vector2(
    (event.clientX / window.innerWidth) * 2 - 1,
    -(event.clientY / window.innerHeight) * 2 + 1
  );
  //console.log(xzPlane.position);
  const raycaster = new THREE.Raycaster();
  raycaster.setFromCamera(mouse, camera);
  raycaster.near = 0.1;
  raycaster.far = 1000;
      
  const direction = raycaster.ray.direction.clone().multiplyScalar(1000); // Extend the line for visibility
  const lineGeometry = new THREE.BufferGeometry().setFromPoints([camera.position, camera.position.clone().add(direction)]);
  const lineMaterial = new THREE.LineBasicMaterial({ color: 0xff0000 });
  const line = new THREE.Line(lineGeometry, lineMaterial);
  scene.add(line);
      
  const intersects = raycaster.intersectObject(cube3);
  console.log(intersects);
  if (intersects.length > 0) {
    const intersectionPoint = intersects[0];
    console.log("facilll");
    console.log(intersectionPoint);
  }
  
});
*/
// Display the building list initially

const buttonsContainer = document.getElementById('buttons');
buildingList.forEach(function (building) {
    const button = document.createElement('button');
    button.setAttribute('data-type', building.type);
    button.setAttribute('data-building', building.name);
    button.textContent = building.name;
    button.classList.add('building-button');
    buttonsContainer.appendChild(button);
});
document.addEventListener('click', function (event) {
    if (event.target.classList.contains('building-button')) {
        const buildingName = event.target.getAttribute('data-building');
        const building = buildingList.find(item => item.name === buildingName);
        if (building) {
            const { location } = building;
            placeMapPointer(new THREE.Vector3(location.x, location.y, location.z));
            document.getElementById('location-popup').style.display = 'none';
            canvas.style.display = "none";
        }
    }
});
function placeMapPointer(position) {
    // Set the target position and duration for camera transition
    const targetPosition = new THREE.Vector3(0, 400, 0);
    const duration = 2000; // Transition duration in milliseconds
    // Store the original camera position
    const originalCameraPosition = camera.position.clone();
    new TWEEN.Tween(camera.position)
        .to(targetPosition, duration)
        .easing(TWEEN.Easing.Quadratic.InOut)
        .start();
    // Remove any existing map pointers
    if (mapPointer) {
        scene.remove(mapPointer);
        clearTimeout(mapPointerTimeout);
    }
    // Create and position the map pointer at the specified location
    gltfLoader.load('./mapPointer.glb', function (gltf) {
        mapPointer = gltf.scene;
        mapPointer.scale.set(35, 35, 35);
        mapPointer.position.set(position.x, 30, position.z);
        scene.add(mapPointer);
        // Set a timeout to check player proximity and remove the map pointer
        mapPointerTimeout = setTimeout(function () {
            const proximityRadius = 10; // Adjust this value as needed
            // Calculate the distance between the player and map pointer in the xz-plane
            const distanceX = Math.abs(player.position.x - position.x);
            const distanceZ = Math.abs(player.position.z - position.z);
            const distance = Math.sqrt(distanceX ** 2 + distanceZ ** 2);
            if (distance <= proximityRadius) {
                scene.remove(mapPointer);
            }
            console.log(distance);
        }, 0);
    });
    setTimeout(function () {
        new TWEEN.Tween(camera.position)
            .to(originalCameraPosition, duration)
            .easing(TWEEN.Easing.Quadratic.InOut)
            .start();
    }, 3000);
}
// Get all checkboxes
var checkboxes = document.querySelectorAll('#poi-popup input[type="checkbox"]');
// Get all buttons
var buttons = document.querySelectorAll('#buttons button');
// Add event listener to each checkbox
checkboxes.forEach(function (checkbox) {
    checkbox.addEventListener('change', filterBuildings);
});
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

function eventHandler(building){
    const eventPopup = document.getElementById('event-popup');
    const h1Element = eventPopup.querySelector('#event-popup h1');
    const ulElement = eventPopup.querySelector('#event-popup ul');
    ulElement.innerHTML = '';
    h1Element.textContent = building;
                
    for (let i = 0; i < eventArray.length; i++) {
        if(eventArray[i].location==building||building=="ALL"){
            console.log("yesss");
            const option1 = document.createElement('li');
            const option1Link = document.createElement('a');
            option1Link.setAttribute('data-popup', 'event-popup');
            option1Link.addEventListener('click', function(event) {
                console.log(event);
                event.preventDefault();
                const popupId = this.getAttribute('data-popup');
                console.log(popupId);
                const popup = document.getElementById(popupId);
                console.log(popup);
                //canvas.style.display = 'none';
                popup.style.display = 'none'; // Hide the popup
                const singleEventPopup = document.getElementById('single-event-popup');
                const eventInfo = singleEventPopup.querySelector('#single-event-popup div');
                eventInfo.innerHTML = '';
                const fieldNames = ['Name', 'Location', 'Description', 'Start Date', 'End Date'];
                // Create the <p> elements with <strong> and <span> elements inside
                fieldNames.forEach(fieldName => {
                    const fieldParagraph = document.createElement('p');
                    const strongElement = document.createElement('strong');
                    strongElement.textContent = `${fieldName}: `;
                    const spanElement = document.createElement('span');
                    const convertedString = fieldName
                    .split(' ')
                    .map((word, index) => {
                      if (index === 0) {
                        return word.toLowerCase();
                      }
                      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
                    })
                    .join('');
                  
                    console.log(convertedString);
                    spanElement.textContent = `${eventArray[i][convertedString]}`;
                    fieldParagraph.appendChild(strongElement);
                    fieldParagraph.appendChild(spanElement);
                    eventInfo.appendChild(fieldParagraph);
                });
                showPopup(singleEventPopup);
              });
            option1Link.textContent = eventArray[i].name;
            option1.appendChild(option1Link);
            ulElement.appendChild(option1);
        }  
    }
    showPopup(eventPopup);
} 
function addItem(item) {
    var newitempopup = document.querySelector('.newitempopup');
    // Show the popup
    function showItemPopup() {
        newitempopup.classList.add('show');
    }
    // Hide the popup
    function hideItemPopup() {
        newitempopup.classList.remove('show');
    }
    // Show the popup initially
    showItemPopup();
    // Hide the popup after 3 seconds
    setTimeout(hideItemPopup, 3000);
    myInventory.push(item);
} 
function inventoryHandler(inventoryPopup) {
    const ulElement = document.getElementById('items');
    ulElement.innerHTML = "";
    console.log("dhvfaehkbfkusbhrfebjkse");
    for (let i = 0; i < myInventory.length; i++) {
        const itemKey = myInventory[i];
        const item=inventory[itemKey];
        const newLiElement = document.createElement('li');
        const imgElement = document.createElement('img');
        imgElement.src =  item.src;
        imgElement.alt =  item.alt;
        const spanElement = document.createElement('span');
        spanElement.textContent =  item.alt;
        newLiElement.appendChild(imgElement);
        newLiElement.appendChild(spanElement);
        ulElement.appendChild(newLiElement);
    }
    showPopup(inventoryPopup);
}

function addControls(controlCoord) {
    // Camera
    //camera = new THREE.PerspectiveCamera(viewAngle, aspect, near, far)
    camera= new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
    // Controls
    let options = {
        delta: 0.75,           // coefficient of movement
        moveSpeed: 0.05,        // speed of movement
        rotationSpeed: 0.004,  // coefficient of rotation
        maxPitch: 55,          // max camera pitch angle
        hitTest: false,         // stop on hitting objects
        hitTestDistance: 40    // distance to test for hit
    }
    controls = new TouchControls(canvas.parentNode, camera, options);
    //controls.setPosition(-25, 2, 20);
    controls.setPosition(controlCoord.x, controlCoord.y, controlCoord.z);
    controls.addToScene(scene);
    // controls.setRotation(0.15, -0.15)
}
function enterRoom(roomPos) {
    if(isCodeExecutionEnabled){
        //scene.remove(model);
        //scene.add(laboratory);
        //scene.add(laboratory);
        //laboratory boundaries
        addControls(roomPos);
    } else  {
        const elements1 = document.getElementById('rotation-pad1');
        if (elements1) {
            canvas.parentNode.removeChild(elements1);
        }
        const elements2 = document.getElementById('movement-pad1');
        if (elements2) {
            canvas.parentNode.removeChild(elements2);
        }
        //scene.remove(laboratory);
        //scene.add(model);
        camera= new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
        controls = new OrbitControls(camera, renderer.domElement);
        controls.target.copy(player.position);
    }
    isCodeExecutionEnabled=!isCodeExecutionEnabled;
}

document.addEventListener('mousedown', (event) => {
    const mouse = new THREE.Vector2(
      (event.clientX / window.innerWidth) * 2 - 1,
      -(event.clientY / window.innerHeight) * 2 + 1
    );
    //console.log(xzPlane.position);
    const raycaster = new THREE.Raycaster();
    raycaster.setFromCamera(mouse, camera);
    raycaster.near = 0.1;
    raycaster.far = 1000;
        
    const signs = raycaster.intersectObject(signBoxGroup, true);
    console.log(signs);
    if (signs.length > 0) {
        //p.textContent='lalala';
        console.log(signs[0].object.name);
        //const eventPopup = document.getElementById('event-popup');
        //const h1Element = eventPopup.querySelector('#event-popup h1');
        //const ulElement = eventPopup.querySelector('#event-popup ul');
        switch(signs[0].object.name){
            case 'ed7':
                eventHandler("ED7");
                break;
            case 'ed2':
                eventHandler("ED2");
                break;
            default:
                break;

        }

    }
    const passages = raycaster.intersectObject(secretDoorsGroup, true);
    console.log(passages);
    if (passages.length > 0) {
        //console.log(intersects3[0].object.name);
        switch(passages[0].object.name){
            case 'rooftop':
                console.log("rooftop");
                enterRoom(rooms.lab.position);
                break;
            case 'lab':
                //addItem('item3');
                if(isCodeExecutionEnabled){
                    scene.remove(model);
                    scene.add(laboratory);
                } else{
                    scene.add(model);
                    scene.remove(laboratory);
                }
                console.log("lab");
                enterRoom(rooms.lab.position);
                break;
            default:
                break;

        }

    }
    const npcHitBox = raycaster.intersectObject(cube3, true);
    console.log(npcHitBox);
    if (npcHitBox.length > 0) {
        console.log("lalalalallala");
        addItem('item3');
    }
    
  });
// Function to show a pop-up
function showPopup(popup) {
    const singleEventPopup = document.getElementById('single-event-popup');
    const inventoryPopup = document.getElementById('inventory-popup');
    const characterPopup = document.getElementById('character-popup');
    if(popup != singleEventPopup){
        menuPop();
    }
    if (popup != inventoryPopup) {
        inventoryPopup.style.display = 'none';
    }
    if (popup != characterPopup) {
        characterPopup.style.display = 'none';
    }
    canvas.style.display = 'block';
    popup.style.display = 'block';
}



function menuPop() {
    //document.querySelector('.menu-box').classList.toggle('menu-toggler');
    // Select the menu element
    const menu = document.querySelector('.menu');
    const menuIcon = document.querySelector('.menu-button input');
    console.log(menuIcon);
    if (isOpen) {
        menu.style.transform = 'translateX(-50%) scale(0)';
        menuIcon.checked = false;
    } else {
        menu.style.transform = 'translateX(-50%) scale(1)';
        menuIcon.checked = true;
    }
    isOpen = !isOpen;

}

function decodeBase64UrlSafe(base64UrlSafe) {
    // Replace characters that are URL-safe encoding variants
    var base64 = base64UrlSafe.replace(/-/g, '+').replace(/_/g, '/');
  
    // Add padding if necessary
    var padding = base64.length % 4;
    if (padding === 2) {
      base64 += '==';
    } else if (padding === 3) {
      base64 += '=';
    }
  
    // Decode Base64
    var decodedData = atob(base64);
  
    // Convert to UTF-8 string
    var decodedString = decodeURIComponent(escape(decodedData));
  
    return decodedString;
  }
  function encodeBase64UrlSafe(data) {
    // Convert the UTF-8 string to Base64
    var encodedData = btoa(unescape(encodeURIComponent(data)));
  
    // Replace characters that are not URL-safe
    var base64UrlSafe = encodedData.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  
    return base64UrlSafe;
  }

function init() {
    
    /*
    fetch('https://fctconnect23.oa.r.appspot.com/rest/listevents', {
            method: 'GET',
            headers: {
              'x-auth-token': 'Bearer ***REMOVED***'
            }
          })
          .then(response => {
            if (response.ok) {
              console.log(response.body); // get the value of the Content-Type header
              return response.json();
            } else {
              //alert("not able to get events");

            }
          })
          .then(data => {
            eventArray=data;
            console.log("arayy de evnetosssss");
            console.log(eventArray);
          })
          .catch(error => {
            // handle login failure
            //alert(error.message);
    });
    */

    // Retrieve the value from local storage
    const yup = localStorage.getItem('username');
    alert(yup);
    // const aaa = "pm.catarino";
    playerUsername=encodeBase64UrlSafe(yup);

    const app = initializeApp(appSettings);
    const database = getDatabase(app);
    console.log(database);
    const shoppingListInDB = ref(database, "players");
    console.log(shoppingListInDB);

    const singleFirebasePath="players/"+playerUsername;
    const ppp = ref(database, singleFirebasePath);
    console.log(ppp);
    onValue(ppp, (snapshot) => {
        const data = snapshot.val(); // Retrieve the data from the snapshot
        console.log(data);
        const x = (parseFloat(data.coordY)+9.20575)/0.00001148273;
        const z= -(parseFloat(data.coordX)-38.66102)/0.00000809869;
        console.log(x);
        console.log(z);
        latestUserPosition.set(x, 0, z);
    });
    
    // Listen for value changes on the "players" reference
    /*
    onValue(shoppingListInDB, (snapshot) => {
        const data = snapshot.val(); // Retrieve the data from the snapshot
        console.log("firebase data");
        const friendIds = Object.keys(data);
        console.log(friendIds);
        //console.log(data); // Log the retrieved data
        console.log(data.cG0uY2F0YXJpbm8);
        const x = (parseFloat(data.cG0uY2F0YXJpbm8.coordY)+9.20575)/0.00001148273;
        const z= -(parseFloat(data.cG0uY2F0YXJpbm8.coordX)-38.66102)/0.00000809869;
        console.log(x);
        console.log(z);
        latestUserPosition.set(x, 0, z);

        alert(data);
    });
    */
    const friendsFirebasePath="friendships/"+playerUsername+"/friends";
    const loaderrr = new GLTFLoader();
      const friendsList = ref(database, friendsFirebasePath);    
      onValue(friendsList, (snapshot) => {
        const data = snapshot.val(); // Retrieve the data from the snapshot
        console.log("firebase data");
        const friendIds = Object.keys(data);
        console.log(data);
        console.log(friendIds);
    
        // Iterate over each friendId
        friendIds.forEach((friendId) => {
            const ppp = ref(database, `players/${friendId}`);
            console.log(ppp);
            onValue(ppp, (snapshot) => {
                const friendData = snapshot.val(); // Retrieve the data from the snapshot
                console.log(friendData);
                console.log(friendData.coordY);
                console.log(friendData.coordX);
                const x = (parseFloat(friendData.coordY)+9.20575)/0.00001148273;
                const z= -(parseFloat(friendData.coordX)-38.66102)/0.00000809869;
                console.log(x);
                console.log(z);
                
                
                
                const friendModelData = data[friendId];
                console.log(friendId);
            
                // Check if the model for the friend already exists
                if (friendModels[friendId]) {
                    // Update the position of the existing model
                    /*
                    const modell = friendModels[friendId];
                    //modell.latestPosition.set(x, 0, z);
                    const aux= new THREE.Vector3(x, 0, z);
                    console.log(aux);
                    console.log(modell.latestPosition);
                    */
                    friendModels[friendId].latestPosition.set(x, 0, z);
                    console.log("model already exists");
                } else {
                    console.log("model added");
                    loaderrr.load('./character/friendsModelReduc.glb', (gltf) => {
                        const fff = gltf.scene;
                        fff.position.set(x, 0, z);
                        scene.add(fff);
                        const name=decodeBase64UrlSafe(friendId);
                        console.log(friendId);
                        /*
                        const p =document.createElement('p');
                        p.textContent=name;
                        const cPointLabel2=new CSS2DObject(p);
                        scene.add(cPointLabel2);
                        console.log(cPointLabel2);
                        cPointLabel2.position.set(x, 0, z);
                        */
                        const p = document.createElement('p');
                        p.textContent = name;
                        //p.style.color = 'white';
                        const cPointLabel2 = new CSS2DObject(p);
                        cPointLabel2.element.style.fontFamily = "'Bebas Neue', sans-serif";
                        cPointLabel2.element.style.fontSize = "0.9em";
                        cPointLabel2.element.style.fontWeight = "bold";
                        cPointLabel2.element.style.fontStyle = "italic";
                        cPointLabel2.element.style.transform = "skew(-10deg)";
                        cPointLabel2.element.style.textShadow =
                            "-1px -1px 0 black, 1px -1px 0 black, -1px 1px 0 black, 1px 1px 0 black";
                        cPointLabel2.element.style.color = "white";
                        cPointLabel2.position.set(x, 0, z);
                        scene.add(cPointLabel2);
                        
                        friendModels[friendId] = {
                            username: name,
                            label: cPointLabel2,
                            model: fff,
                            latestPosition: new THREE.Vector3(x, 0, z)
                        };
                        
                    });
            
                    // Store a reference to the model
                    
                }
                
            });
          
        });
      });
    //const loadingVideo = document.getElementById('loading-video');
    canvas = document.getElementById('info');
    //const popups = document.querySelectorAll('.popup');
    //const closePopups = document.querySelectorAll('.close-popup');
    const menuButton = document.getElementById('menu-button');
    const inventoryPopup = document.getElementById('inventory-popup');
    const locationPopup = document.getElementById('location-popup');
    const characterPopup = document.getElementById('character-popup');
    //const eventsPopup = document.getElementById('event-popup');
    //const popups = document.querySelectorAll('.popup');
    const closePopups = document.querySelectorAll('.close-popup');
    console.log(closePopups);
    // Add event listener to open the menu pop-up
    menuButton.addEventListener('click', () => menuPop());
    document.getElementById('inventory').addEventListener('click', () => inventoryHandler(inventoryPopup));
    document.getElementById('location').addEventListener('click', () => showPopup(locationPopup));
    document.getElementById('character').addEventListener('click', () => showPopup(characterPopup));
    document.getElementById('events').addEventListener('click', () => eventHandler("ALL"));
    const submitButton = document.getElementById('submit-button');
    // Add event listener to close all pop-ups
    //closePopups.forEach(closePopup => closePopup.addEventListener('click', hidePopups()));
    
    closePopups.forEach(closePopup => {
        closePopup.addEventListener('click', function(event) {
          event.preventDefault();
          const popupId = this.getAttribute('data-popup');
          console.log(popupId);
          const popup = document.getElementById(popupId);
          console.log(popup);
          // Now you have access to the popup ID and the corresponding popup element
          // You can perform additional actions based on the specific popup being closed
          canvas.style.display = 'none';
          popup.style.display = 'none'; // Hide the popup
        });
      });
    /*
    submitButton.addEventListener('click', function(event) {
        event.preventDefault(); // Prevent form submission
      
        const input1Value = parseFloat(document.getElementById('input1').value);
        const input2Value = parseFloat(document.getElementById('input2').value);
        const x=(input2Value + 9.20575) / 0.00001148273;
        const z=-(input1Value - 38.66102) / 0.00000809869;
        latestUserPosition = new THREE.Vector3(x, 0, z);
    });
    */
    // Note: You can also add this event listener to close the pop-ups when clicking outside the pop-up area
    //canvas.addEventListener('click', hidePopups);
    
    document.body.appendChild(canvas);

    scene = new THREE.Scene();
    camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);


    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(window.innerWidth, window.innerHeight);
    document.body.appendChild(renderer.domElement);
    controls = new OrbitControls(camera, renderer.domElement);
    // Set the minimum and maximum polar angles
    controls.minPolarAngle = 0;            // The minimum angle (in radians)
    controls.maxPolarAngle = Math.PI / 2;  // The maximum angle (in radians)
    controls.enablePan = false;
	controls.enableDamping = true;
    controls.maxDistance = 400;
    controls.minDistance = 5;

    // Disable rotation and enable vertical panning
    //controls.enableRotate = false;
    //controls.enablePan = true;
    //controls.screenSpacePanning = true;


    ambientLight = new THREE.HemisphereLight(
        'white',
        'darkslategrey',
        3,
    );

    new RGBELoader()
        .load('./cloudysky.hdr', function (texture) {

            texture.mapping = THREE.EquirectangularReflectionMapping;

            scene.background = texture;
            scene.environment = texture;

        });

    gltfLoader = new GLTFLoader();
    const dracoLoader = new DRACOLoader();
    dracoLoader.setDecoderPath('https://www.gstatic.com/draco/versioned/decoders/1.5.6/');
    dracoLoader.setDecoderConfig({ type: 'js' });
    gltfLoader.setDRACOLoader(dracoLoader);
    gltfLoader.load(
        // resource URL
        './compressedFinalDraco.glb',
        // called when the resource is loaded
        function (draco) {
            model = draco.scene;
            /*
            draco.scene.traverse(function (object) {

                if (object.isMesh) object.castShadow = true;

            });*/
            updatables.push(model);
            scene.add(model);
            console.log(draco);
        },
        // called while loading is progressing
        function (xhr) {

            console.log((xhr.loaded / xhr.total * 100) + '% loaded');

        },
        // called when loading has errors
        function (error) {

            console.log('An error happened');
        }
    );
    /*
    gltfLoader.load(
        // resource URL
        '/diBuildingProgressTestingCompressedNormals.glb',
        // called when the resource is loaded
        function (draco) {
            const di = draco.scene;
            di.scale.set(0.7, 0.7, 0.7);
            di.position.set(225, 0, -15);
            // Rotate the model
            const rotationAxis = new THREE.Vector3(0, 1, 0); // Adjust the axis as needed
            const rotationAngle = Math.PI / 1.35; // Adjust the angle as needed
            di.rotateOnAxis(rotationAxis, rotationAngle);
            scene.add(di);
            console.log(draco);
        },
        // called while loading is progressing
        function (xhr) {

            console.log((xhr.loaded / xhr.total * 100) + '% loaded');

        },
        // called when loading has errors
        function (error) {

            console.log('An error happened');
        }
    );
    */
    gltfLoader.load('./fullRoom2.glb', function (gltf) {
        laboratory=gltf.scene;
        laboratory.scale.set(0.05, 0.05, 0.05);
        laboratory.position.set(-25, 0, 30);
        //scene.add(laboratory);
    });
    gltfLoader.load('./yyy.glb', function (gltf) {
        console.log(gltf.animations);
        anim = gltf.animations;
    });

    gltfLoader.load('./michelleComp.glb', function (gltf) {
        player = gltf.scene;
        // Clone or create new instances of the objects in the GLTF scene
        //clonedPlayer = gltf.scene.clone(); // Clone the entire scene
        //const skeleton = new THREE.SkeletonHelper( gltf.scene );
        //const bones = skeleton.bones;
        //console.log(gltf.scene);

        //loop through all the bones and log their names
        //bones.forEach( ( bone ) => {
        //console.log( bone.name );
        //} );
        //mixer = new THREE.AnimationMixer( monster );
        //mixer.clipAction( anim ).play();
        createUltimate(anim);
        scene.add(player);
        //controls.target.copy(player.position);
        /*
        // create a bone for your cosmetic
        const cosmeticBone = new THREE.Bone();
    
        // set the position and rotation of your cosmetic bone relative to your character's bone
        //cosmeticBone.position.set(x, y, z);
        //cosmeticBone.rotation.set(rx, ry, rz);
    
        // find the bone you want to attach the cosmetic to
        const boneToAttach = monster.getObjectByName('mixamorigLeftHand');
    
        // add your cosmetic bone to the bone you want to attach it to
        boneToAttach.add(cosmeticBone);
    
        // create a group to hold your cosmetic mesh
        const cosmeticGroup = new THREE.Group();
    
        // load your cosmetic mesh
        gltfLoader.load('/pa.glb', function(cosmeticGltf) {
          const cosmetic = cosmeticGltf.scene;
          
          // add your cosmetic mesh to the cosmetic group
          cosmeticGroup.add(cosmetic);
          
          // set the position and rotation of your cosmetic mesh relative to your cosmetic bone
          //cosmetic.position.set(x, y, z);
          //cosmetic.rotation.set(rx, ry, rz);
        });
    
        // add your cosmetic group to the scene
        scene.add(cosmeticGroup);
        */

    });
    gltfLoader.load('./npc.glb', function (gltf) {
        npc = gltf.scene;
        const animations = gltf.animations;
        // Add the model to your scene
        scene.add(npc);

        // Play the first animation (assuming there's at least one)
        mixer2 = new THREE.AnimationMixer(npc);
        const animation = animations[1];
        console.log(animation);
        const action = mixer2.clipAction(animation);
        action.play();

    });

    //const mainLight = new THREE.DirectionalLight('white', 6);
    //mainLight.position.set(10, 10, 10);
    ambientLight.position.set(0, 100, 0)
    scene.add(ambientLight);
    //scene.add( mainLight );
    
    // Create a cube geometry
    const geometry3 = new THREE.BoxGeometry(1, 1, 1);

    // Create a green material
    const material3 = new THREE.MeshBasicMaterial({ color: 0x00ff00 });

    // Create a cube mesh using the geometry and material
    cube3 = new THREE.Mesh(geometry3, material3);
    //cube4 = new THREE.Mesh(geometry3, material3);
    scene.add(cube3);
    //scene.add(cube4);
    //cube3.position.set(0, 10, 0);
    
    labelRenderer=new CSS2DRenderer();
    labelRenderer.setSize(window.innerWidth, window.innerHeight);
    labelRenderer.domElement.style.position='absolute';
    //labelRenderer.domElement.style.zIndex='1100';
    labelRenderer.domElement.style.top='0px';
    labelRenderer.domElement.style.pointerEvents='none';
    document.body.appendChild(labelRenderer.domElement);

    signBoxGroup= new THREE.Group();
    const signGroup= new THREE.Group();
    
    function createInstance(sign, name, x, y, z, r) {
        const modelPlaca = sign.clone();
        modelPlaca.position.set(x, y, z);
        const rotationAxis = new THREE.Vector3(0, 1, 0); // Adjust the axis as needed
        const rotationAngle = Math.PI / r; // Adjust the angle as needed
        modelPlaca.rotateOnAxis(rotationAxis, rotationAngle);
        signGroup.add(modelPlaca);

        const geo= new THREE.SphereGeometry(2.2);
        //const mat= new THREE.MeshBasicMaterial({color: 0xFF0000});
        const mat = new THREE.MeshBasicMaterial({ transparent: true, opacity: 0 });
        const mesh= new THREE.Mesh(geo,mat);
        mesh.position.set(x, y+2, z);
        mesh.name= name;
        signBoxGroup.add(mesh);
    }

    gltfLoader.load('./sign.glb', function (gltf) {
        const sign = gltf.scene;
        buildingList.forEach(building => {
            const { name, location, rotation } = building;
            createInstance(sign, name, location.x, location.y, location.z, rotation);
        });
    });
    scene.add(signGroup);
    scene.add(signBoxGroup);

    secretDoorsGroup= new THREE.Group();
    
    function createDoors(name, x, y, z) {
        const geo= new THREE.SphereGeometry(1.5);
        //const mat= new THREE.MeshBasicMaterial({color: 0xFF0000});
        const mat = new THREE.MeshBasicMaterial();
        const mesh= new THREE.Mesh(geo,mat);
        mesh.position.set(x, y+2, z);
        mesh.name= name;
        secretDoorsGroup.add(mesh);
    }
    createDoors("lab", 0, 5, 0);
    createDoors("rooftop", 0, 5, 5);
    scene.add(secretDoorsGroup);
    
    
    const color = 0x12163A;
    const floorMaterial = new THREE.MeshBasicMaterial({ color: color, side: THREE.DoubleSide });
    const floorGeometry = new THREE.PlaneGeometry(10000, 10000);
    const floorMesh = new THREE.Mesh(floorGeometry, floorMaterial);
    floorMesh.rotation.x = -Math.PI / 2; // Rotate the floor to lay it flat
    floorMesh.position.y = -1; // Adjust the floor's position as needed
    scene.add(floorMesh);

    /*
    p =document.createElement('span');
    p.className='title';
    const pContainer=document.createElement('div');
    
    pContainer.className='card__content';
    pContainer.appendChild(p);
    const pContainer2=document.createElement('div');
    pContainer2.className='card';
    pContainer2.appendChild(pContainer);
    cPointLabel=new CSS2DObject(pContainer2);
    //scene.add(cPointLabel);
    //cPointLabel.position.set(0, 5, 0);
    p.textContent='Kroben';
    */

    
    const p = document.createElement('p');
    p.textContent = 'Kroben';
    //p.style.color = 'white';
    cPointLabel = new CSS2DObject(p);
    cPointLabel.element.style.fontFamily = "'Bebas Neue', sans-serif";
    cPointLabel.element.style.fontSize = "0.9em";
    cPointLabel.element.style.fontWeight = "bold";
    cPointLabel.element.style.fontStyle = "italic";
    cPointLabel.element.style.transform = "skew(-10deg)";
    cPointLabel.element.style.textShadow =
        "-1px -1px 0 black, 1px -1px 0 black, -1px 1px 0 black, 1px 1px 0 black";
    cPointLabel.element.style.color = "white";
    scene.add(cPointLabel);

    
   
    
    // Set up the keyboard controls for player movement

    function onKeyDown(event) {
        keyboard[event.code] = true;
    }
    function onKeyUp(event) {
        keyboard[event.code] = false;
    }
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('keyup', onKeyUp);
    /*
    // After loading is complete
    setTimeout(() => {
        loadingVideo.style.display = 'none'; // Hide the loading video
    }, 10000); // Adjust the delay time (in milliseconds) as needed
    */

}
function hasArrivedDestination() {
    // Set a timeout to check player proximity and remove the map pointer
    if (mapPointer) {
        mapPointerTimeout = setTimeout(function () {
            const proximityRadius = 10; // Adjust this value as needed
            // Calculate the distance between the player and map pointer in the xz-plane
            const distanceX = Math.abs(latestUserPosition.x - mapPointer.position.x);
            const distanceZ = Math.abs(latestUserPosition.z - mapPointer.position.z);
            const distance = Math.sqrt(distanceX ** 2 + distanceZ ** 2);
            if (distance <= proximityRadius) {
                scene.remove(mapPointer);
            }
            console.log(distance);
        }, 0);
    }
}
function movePlayer(){
    if(player){
        const direction = new THREE.Vector3().subVectors(latestUserPosition, player.position);
        const speedFactor = 200; // Adjust this value to control the speed of movement
        const distance = direction.length() / speedFactor;
        //console.log(distance)
        if (0.01 < distance) {
            player.position.add(direction.normalize().multiplyScalar(distance));
            player.lookAt(player.position.clone().add(direction));
            controls.target.copy(player.position);
            cPointLabel.position.copy(player.position);
            cPointLabel.position.y=3;
        }
    }
}
function movePlayer2(character){
    if(character){
        const direction = new THREE.Vector3().subVectors(character.latestPosition, character.model.position);
        const speedFactor = 200; // Adjust this value to control the speed of movement
        const distance = direction.length() / speedFactor;
        //console.log(distance)
        if (0.01 < distance) {
            character.model.position.add(direction.normalize().multiplyScalar(distance));
            character.model.lookAt(character.model.position.clone().add(direction));
            //controls.target.copy(player.position);
            character.label.position.copy(character.model.position);
            character.label.position.y=3;
        }
    }
}
function moveNpc(){
    if(npc){
        const targetPosition = npcPositions[currentNpcPositionIndex];
        const direction = new THREE.Vector3().subVectors(targetPosition, npc.position);
        const speedFactor = 200; // Adjust this value to control the speed of movement
        const distance = direction.length() / speedFactor;
        //console.log(distance)
        if (0.01 < distance) {
            npc.position.add(direction.normalize().multiplyScalar(0.01));
            cube3.position.copy(npc.position);
            npc.lookAt(npc.position.clone().add(direction));
        }else {
            // Player has reached the target position, so update the index for the next position
            currentNpcPositionIndex = (currentNpcPositionIndex + 1) % npcPositions.length;
        }
    }
}
function animate() {

   //Keyboard
    if (keyboard['KeyW']) {
        player.position.z -= 0.3;
        latestUserPosition.copy(player.position);
        controls.target.copy(player.position);
        console.log(player.position);
        cPointLabel.position.copy(player.position);
        cPointLabel.position.y=3;
    }
    if (keyboard['KeyS']) {
        player.position.z += 0.3;
        latestUserPosition.copy(player.position);
        controls.target.copy(player.position);
        console.log(player.position);
        cPointLabel.position.copy(player.position);
        cPointLabel.position.y=3;
    }
    if (keyboard['KeyA']) {
        player.position.x -= 0.3;
        latestUserPosition.copy(player.position);
        controls.target.copy(player.position);
        console.log(player.position);
        cPointLabel.position.copy(player.position);
        cPointLabel.position.y=3;
    }
    if (keyboard['KeyD']) {
        player.position.x += 0.3;
        latestUserPosition.copy(player.position);
        controls.target.copy(player.position);
        console.log(player.position);
        cPointLabel.position.copy(player.position);
        cPointLabel.position.y=3;
    }

    if (keyboard['KeyT']) {
        //latestUserPosition.set(0, 0, 15);
        //scene.clear();
        //console.log(friendModels['cG0uY2F0YXJpbm8']);
        friendModels['cG0uY2F0YXJpbm8'].latestPosition.set(0, 0, 0);
    }
    if (keyboard['KeyO']) {
        //latestUserPosition.set(0, 0, 15);
        //console.log(friendModels);
        for (let key in friendModels) {
            console.log(key, friendModels[key]);
        }
        friendModels['cG0uY2F0YXJpbm8'].latestPosition.set(0, 0, 10);
    }

    if (isCodeExecutionEnabled) { // Make sure characterMesh is defined
        movePlayer();
        moveNpc();
        
        for (let key in friendModels) {
            //console.log(key, friendModels[key].latestPosition);
            movePlayer2(friendModels[key]);
        }
        
    }else if(player){
        //player.position.copy(mobileControls.getPosition());
        //player.position.y=0;
        const yyy=controls.getPosition();
        console.log(yyy);
        if(yyy.x>rooms.lab.topX)
            controls.setPosition(rooms.lab.topX, yyy.y, yyy.z);
        if(yyy.x<rooms.lab.bottomX)
            controls.setPosition(rooms.lab.bottomX, yyy.y, yyy.z);
        if(yyy.z>rooms.lab.topZ)
            controls.setPosition(yyy.x, yyy.y, rooms.lab.topZ);
        if(yyy.z<rooms.lab.bottomZ)
            controls.setPosition(yyy.x, yyy.y, rooms.lab.bottomZ);
    }

    controls.update();

    //tick();
    const dt = clock.getDelta();
    TWEEN.update();
    if (mixer) mixer.update(dt);
    if (mixer2) mixer2.update(dt);
    requestAnimationFrame(animate);
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
    // Loop through friendModels object
    /*
    Object.keys(friendModels).forEach((friendId) => {
        const friendData = friendModels[friendId];
        const friendUsername = friendData.username;
        const friendModel = friendData.model;
        const friendLabel = friendData.cssLabel;
        friendLabel.render(scene, camera);
        
        // Do something with friend's data
        console.log(`Friend ID: ${friendId}`);
        console.log(`Username: ${friendUsername}`);
        console.log("Model:", friendModel);
    });
    */
}
function createUltimate(animations) {
    //player.position.x = (-9.20348 + 9.20575) / 0.0000117094;
    //player.position.y = 0;
    //player.position.z = -(38.66113 - 38.66102) / 0.00000333333;
    //player.position.set(0, 0, 0);
    console.log(player.position);
    //latestUserPosition.copy(player.position);
    //controls.target.copy(player.position);

    const states = ['boxing', 'capoeira'];
    const players = ['monster.glb', 'michelleComp.glb'];

    gui = new GUI();

    mixer = new THREE.AnimationMixer(player);

    actions = {};


    for (let i = 0; i < animations.length; i++) {
        const clip = animations[i];
        const action = mixer.clipAction(clip);
        actions[clip.name] = action;
    }

    // states

    const statesFolder = gui.addFolder('Emotes');
    const clipCtrl = statesFolder.add(api, 'state').options(states);
    const pFolder = gui.addFolder('Personagens');
    const playersF = pFolder.add(pInit, 'state').options(players);

    clipCtrl.onChange(function () {
        fadeToAction(api.state, 0.5);
    });
    
    playersF.onChange(function () {
        scene.remove(player);
        gltfLoader.load(pInit.state, function (gltf) {
            player = gltf.scene;
            createUltimate(animations);
            scene.add(player);
           
        });
    });
    
    activeAction = actions['boxing'];
    activeAction.play();
    statesFolder.open();
    pFolder.open();

}

/*
function createGUI(player, animations) {
    //player.position.y = 5;
    //console.log(player.position.y);
    player.position.x = (-9.20322 + 9.20575) / 0.0000117094;
    player.position.y = 0;
    player.position.z = -(38.66101 - 38.66102) / 0.00000333333;
    latestUserPosition.copy(player.position);
    controls.target.copy(player.position);

    const states = ['Idle', 'Walking', 'Running', 'Dance', 'Death', 'Sitting', 'Standing'];
    const emotes = ['Jump', 'Yes', 'No', 'Wave', 'Punch', 'ThumbsUp'];

    gui = new GUI();

    mixer = new THREE.AnimationMixer(player);

    actions = {};

    for (let i = 0; i < animations.length; i++) {

        const clip = animations[i];
        const action = mixer.clipAction(clip);
        actions[clip.name] = action;

        if (emotes.indexOf(clip.name) >= 0 || states.indexOf(clip.name) >= 4) {

            action.clampWhenFinished = true;
            action.loop = THREE.LoopOnce;

        }

    }

    const statesFolder = gui.addFolder('States');

    const clipCtrl = statesFolder.add(api, 'state').options(states);

    clipCtrl.onChange(function () {
        fadeToAction(api.state, 0.5);

    });

    statesFolder.open();

    // emotes

    const emoteFolder = gui.addFolder('Emotes');

    function createEmoteCallback(name) {

        api[name] = function () {

            fadeToAction(name, 0.2);

            mixer.addEventListener('finished', restoreState);

        };

        emoteFolder.add(api, name);

    }

    function restoreState() {

        mixer.removeEventListener('finished', restoreState);

        fadeToAction(api.state, 0.2);

    }

    for (let i = 0; i < emotes.length; i++) {

        createEmoteCallback(emotes[i]);

    }

    emoteFolder.open();

    // expressions

    face = player.getObjectByName('Head_4');

    const expressions = Object.keys(face.morphTargetDictionary);
    const expressionFolder = gui.addFolder('Expressions');

    for (let i = 0; i < expressions.length; i++) {

        expressionFolder.add(face.morphTargetInfluences, i, 0, 1, 0.01).name(expressions[i]);

    }

    activeAction = actions['Walking'];
    activeAction.play();

    expressionFolder.open();

}
*/
function fadeToAction(name, duration) {
    previousAction = activeAction;
    activeAction = actions[name];

    if (previousAction !== activeAction) {

        previousAction.fadeOut(duration);

    }

    activeAction
        .reset()
        .setEffectiveTimeScale(1)
        .setEffectiveWeight(1)
        .fadeIn(duration)
        .play();

}
window.onresize = function () {

    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();

    renderer.setSize( window.innerWidth, window.innerHeight );

};
/*
function tick() {
    // only call the getDelta function once per frame!
    const delta = clock.getDelta();
    const elapsedTime = clock.getElapsedTime();

    // console.log(
    //   The last frame rendered in ${delta * 1000} milliseconds,
    // );

    for (const object of updatables) {
        object.tick(delta, elapsedTime);
    }
}
*/
animate();