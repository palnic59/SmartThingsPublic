/**
 *  domoticzMotion
 *
 *  Copyright 2016 Martin Verbeek
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Hue Motion", namespace: "verbem", author: "Martin Verbeek") {
		capability "Motion Sensor"
		capability "Sensor"
		capability "Battery"
		capability "Actuator"
        capability "Illuminance Measurement"
        capability "Temperature Measurement"
        
        command "motionEvent", ["string"]
        command "batteryEvent", ["number"]
        command "lightEvent", ["number"]
        command "tempEvent", ["number"]
        }

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "on", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "On", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "ON", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "Off", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
				attributeState "OFF", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
			}
		}
        
		standardTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"", icon:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/devicetypes/verbem/domoticzsensor.src/battery.png"
		}
         
		standardTile("illiminance", "device.illuminance", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "illuminance", label:'${currentValue} Lux', unit:"Lux"
		}
        
		standardTile("temperature", "device.temperature", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "temperature", label:'${currentValue}', unit:"C"
		}
        
		main "motion"
		details(["motion", "illiminance", "temperature", "battery"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'motion' attribute
    
}

def motionEvent(motion) {

	log.info motion
	if (motion == true) sendEvent(name: "motion", value: "active", descriptionText: "$device.displayName motion detected", isStateChange: true)
    else sendEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion stopped", isStateChange: true)

}

def batteryEvent(level) {
	sendEvent(name: "battery", value: level)
}

def lightEvent(lux) {
	sendEvent(name: "illuminance", value: lux)
}

def tempEvent(lux) {
	sendEvent(name: "temperature", value: lux)
}