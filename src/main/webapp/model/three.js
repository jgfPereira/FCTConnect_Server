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
import { getDatabase, ref, push, onValue, remove } from "https://www.gstatic.com/firebasejs/9.15.0/firebase-database.js"
import TouchControls from './js/TouchControls.js'
const appSettings = {
    databaseURL: "https://fctconnectdb-default-rtdb.europe-west1.firebasedatabase.app/"
}


var canvas, controls;
var camera, scene, renderer, labelRenderer, ambientLight;
var clock = new THREE.Clock();
var updatables = [];

var mixer;
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
let group;
let eventArray = [];
let isCodeExecutionEnabled = true;
let isOpen = false;
let cube3;
let cube4;
let roomBoundaries = {};
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
                //spanElement.id = fieldName.toLowerCase().replace(' ', '-');
                const lowerName = fieldName.toLowerCase();
                console.log(lowerName);
                spanElement.textContent = `${eventArray[i][lowerName]}`;
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

function addControls(controlCoord) {
    // Camera
    //camera = new THREE.PerspectiveCamera(viewAngle, aspect, near, far)
    camera= new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
    // Controls
    let options = {
        delta: 0.75,           // coefficient of movement
        moveSpeed: 0.1,        // speed of movement
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
        
    const intersects = raycaster.intersectObject(group, true);
    console.log(intersects);
    if (intersects.length > 0) {
        //p.textContent='lalala';
        console.log(intersects[0].object.name);
        //const eventPopup = document.getElementById('event-popup');
        //const h1Element = eventPopup.querySelector('#event-popup h1');
        //const ulElement = eventPopup.querySelector('#event-popup ul');
        switch(intersects[0].object.name){
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
    const intersects2 = raycaster.intersectObject(cube3);
    if (intersects2.length > 0) {
        //p.textContent='lalala';
        //const duration = 2000;
        //const duration = 2000; 
        console.log("Porta secreta!!!!");
        if(isCodeExecutionEnabled){
            //scene.remove(model);
            //scene.add(laboratory);
            //scene.add(laboratory);
            //laboratory boundaries
            /*
            roomBoundaries = {
                topX: -20,
                bottomX: -30,
                topZ: 31,
                bottomZ: 14,
                position: new THREE.Vector3(-25, 2, 20)
            };
            */
            //roof top boundaries
            
            roomBoundaries = {
                topX: 38,
                bottomX: -4,
                topZ: -167,
                bottomZ: -190,
                position: new THREE.Vector3(12, 12, -172)
            };
            addControls(rooms.roofTop.position);
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
    
  });
// Function to show a pop-up
function showPopup(popup) {
    const singleEventPopup = document.getElementById('single-event-popup');
    if(popup != singleEventPopup){
        menuPop();
    }
    canvas.style.display = 'block';
    popup.style.display = 'block';
}

function showThreePopup(popup) {
    canvas.style.display = 'block';
    popup.style.display = 'block';
    
    const caractherPopup = document.getElementById('caracther-popup');
    //caractherPopup.style.boxSizing = 'border-box';
    // Create a scene
    const scene = new THREE.Scene();

    // Create a camera
    const camera = new THREE.PerspectiveCamera(75, caractherPopup.clientWidth / caractherPopup.clientHeight, 0.1, 1000);
    camera.position.z = 3;
    camera.position.y = 1;

    // Create a renderer
    const renderer = new THREE.WebGLRenderer();
    renderer.setSize(caractherPopup.clientWidth, caractherPopup.clientHeight);
    caractherPopup.appendChild(renderer.domElement);
    
    const gltfLoader2 = new GLTFLoader();
    gltfLoader2.load('./monster.glb', function (gltf) {
        const player2 = gltf.scene;
        scene.add(player2);
    });
    
    // Create ambient light
    const ambientLight = new THREE.HemisphereLight(
        'white',
        'darkslategrey',
        6,
    );
    ambientLight.position.set(0, 10, 0);
    scene.add(ambientLight);

    // Create directional light
    const directionalLight = new THREE.DirectionalLight('white', 7);
    directionalLight.position.set(0, 5, 2);
    scene.add(directionalLight);
    const controls2 = new OrbitControls(camera, renderer.domElement);
    controls2.target.set(0, 1, 0);

    // Animation loop
    function animate() {
    requestAnimationFrame(animate);
    //cube.rotation.x += 0.01;
    //cube.rotation.y += 0.01;
    renderer.render(scene, camera);
    }
    animate();
    
    
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


function init() {
    
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

    const app = initializeApp(appSettings);
    const database = getDatabase(app);
    console.log(database);
    const shoppingListInDB = ref(database, "players");
    console.log(shoppingListInDB);
          // Listen for value changes on the "players" reference
    onValue(shoppingListInDB, (snapshot) => {
        const data = snapshot.val(); // Retrieve the data from the snapshot
        console.log("firebase data");
        //console.log(data); // Log the retrieved data
        console.log(data.cG0uY2F0YXJpbm8);
        const x = (parseFloat(data.cG0uY2F0YXJpbm8.coordY)+9.20575)/0.00001148273;
        const z= -(parseFloat(data.cG0uY2F0YXJpbm8.coordX)-38.66102)/0.00000809869;
        console.log(x);
        console.log(z);
        latestUserPosition.set(x, 0, z);

        alert(data);
    });
    //const loadingVideo = document.getElementById('loading-video');
    canvas = document.getElementById('info');
    //const popups = document.querySelectorAll('.popup');
    //const closePopups = document.querySelectorAll('.close-popup');
    const menuButton = document.getElementById('menu-button');
    const inventoryPopup = document.getElementById('inventory-popup');
    const locationPopup = document.getElementById('location-popup');
    const caractherPopup = document.getElementById('caracther-popup');
    //const eventsPopup = document.getElementById('event-popup');
    //const popups = document.querySelectorAll('.popup');
    const closePopups = document.querySelectorAll('.close-popup');
    console.log(closePopups);
    // Add event listener to open the menu pop-up
    menuButton.addEventListener('click', () => menuPop());
    document.getElementById('inventory').addEventListener('click', () => showPopup(inventoryPopup));
    document.getElementById('location').addEventListener('click', () => showPopup(locationPopup));
    document.getElementById('character').addEventListener('click', () => showThreePopup(caractherPopup));
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
    submitButton.addEventListener('click', function(event) {
        event.preventDefault(); // Prevent form submission
      
        const input1Value = parseFloat(document.getElementById('input1').value);
        const input2Value = parseFloat(document.getElementById('input2').value);
        const x=(input2Value + 9.20575) / 0.00001148273;
        const z=-(input1Value - 38.66102) / 0.00000809869;
        latestUserPosition = new THREE.Vector3(x, 0, z);
    });
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

    const geometry = new THREE.BoxGeometry(1, 1, 1);
    const material = new THREE.MeshBasicMaterial({ color: 0x00ff00 });
    const cube = new THREE.Mesh(geometry, material);
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
            model.position.y=-0.2;
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

    //const mainLight = new THREE.DirectionalLight('white', 6);
    //mainLight.position.set(10, 10, 10);
    ambientLight.position.set(0, 100, 0)
    scene.add(ambientLight);
    //scene.add( mainLight );
    
    // Create a cube geometry
    var geometry3 = new THREE.BoxGeometry(1, 1, 1);

    // Create a green material
    var material3 = new THREE.MeshBasicMaterial({ color: 0x00ff00 });

    // Create a cube mesh using the geometry and material
    cube3 = new THREE.Mesh(geometry3, material3);
    scene.add(cube3);
    cube3.position.set(0, 10, 0);
    
    labelRenderer=new CSS2DRenderer();
    labelRenderer.setSize(window.innerWidth, window.innerHeight);
    labelRenderer.domElement.style.position='absolute';
    //labelRenderer.domElement.style.zIndex='1100';
    labelRenderer.domElement.style.top='0px';
    labelRenderer.domElement.style.pointerEvents='none';
    document.body.appendChild(labelRenderer.domElement);

    group= new THREE.Group();
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
        group.add(mesh);
    }

    gltfLoader.load('./sign.glb', function (gltf) {
        const sign=gltf.scene;
        createInstance(sign, "ed7", -4, 0, 29, 1);
        createInstance(sign, "ed2", 174, 0, 16, 1.33);
    });
    scene.add(signGroup);
    scene.add(group);
    
    


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

    
    const p =document.createElement('p');
    p.textContent='Kroben';
    cPointLabel=new CSS2DObject(p);
    scene.add(cPointLabel);
    console.log(cPointLabel);
    

    
   
    
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
        scene.clear();
    }
    if (keyboard['KeyO']) {
        latestUserPosition.set(0, 0, 15);
        //init();
    }
    if (keyboard['KeyU']) {
        //latestUserPosition.set(156, 0, 27);
        //camera.position.set(player.position.x, 20, player.position.z);
        //camera.lookAt(player.position);
        //const startPosition = camera.position.clone();
        //const startTarget = camera.target.clone();
        
        console.log("tttttttttttttttttttt");
        const targetPosition = new THREE.Vector3(0, 190, 0);
        //const targetLookAt = player.position.clone();
        const ed2Position = new THREE.Vector3(174, 0, 16);
        const duration = 2000; // Transition duration in milliseconds

        new TWEEN.Tween(camera.position)
            .to(targetPosition, duration)
            .easing(TWEEN.Easing.Quadratic.InOut)
            .start();

            gltfLoader.load('./mapPointer.glb', function (gltf) {
                const mapPointer=gltf.scene;
                mapPointer.scale.set(35, 35, 35);
                mapPointer.position.set(174, 20, 16);
                scene.add(mapPointer);
                
            });
    }
    if (player&&isCodeExecutionEnabled) { // Make sure characterMesh is defined
            const direction = new THREE.Vector3().subVectors(latestUserPosition, player.position);
            const speedFactor = 200; // Adjust this value to control the speed of movement
            const distance = direction.length() / speedFactor;
            if (distance < direction.length()) {
                //console.log(distance);
                //console.log(direction.length());
                player.position.add(direction.normalize().multiplyScalar(distance));
                //console.log("heyyaaaaaa");
                player.lookAt(player.position.clone().add(direction));
                controls.target.copy(player.position);
                cPointLabel.position.copy(player.position);
                cPointLabel.position.y=3;
            }
    }else if(player){
        //player.position.copy(mobileControls.getPosition());
        //player.position.y=0;
        const yyy=controls.getPosition();
        console.log(yyy);
        /*
        if(yyy.x>-20)
            controls.setPosition(-20, yyy.y, yyy.z);
        if(yyy.x<-30)
            controls.setPosition(-30, yyy.y, yyy.z);
        if(yyy.z>31)
            controls.setPosition(yyy.x, yyy.y, 31);
        if(yyy.z<14)
            controls.setPosition(yyy.x, yyy.y, 14);
        */
        if(yyy.x>rooms.roofTop.topX)
            controls.setPosition(rooms.roofTop.topX, yyy.y, yyy.z);
        if(yyy.x<rooms.roofTop.bottomX)
            controls.setPosition(rooms.roofTop.bottomX, yyy.y, yyy.z);
        if(yyy.z>rooms.roofTop.topZ)
            controls.setPosition(yyy.x, yyy.y, rooms.roofTop.topZ);
        if(yyy.z<rooms.roofTop.bottomZ)
            controls.setPosition(yyy.x, yyy.y, rooms.roofTop.bottomZ);
    }

    controls.update();

    //tick();
    const dt = clock.getDelta();
    TWEEN.update();
    if (mixer) mixer.update(dt);
    requestAnimationFrame(animate);
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
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