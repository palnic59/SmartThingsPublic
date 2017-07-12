/**
 *  Hue Sensor Manager
 *
 *  Author: Martin Verbeek 
 *
 *  Copyright 2017 Martin Verbeek
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	1.00 Initial Release, thanks to Anthony S for version control
 *	1.01 Support for Hue B bridges or bridges that support username as an attribute
 *
 */


definition(
		name: "Hue Sensor (Connect)",
		namespace: "verbem",
		author: "Martin Verbeek",
		description: "Allows you to connect your Philips Hue Sensors with SmartThings and control them from your Things area or Dashboard in the SmartThings Mobile app. It will connect to bridges that are discovered by Super Lan Connect",
		category: "My Apps",
		iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/hue.png",
		iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/hue@2x.png",
		singleInstance: true
)
private def runningVersion() 	{ "1.01"}

preferences {
	page(name:pageMain)
    page(name:pageBridges, title:"", refreshInterval:20)
}


def pageMain() {
	state.refreshCount = 0
	if (!state.devices) state.devices = [:]

	pageBridges()
}

def pageBridges() {
    state.refreshCount = state.refreshCount + 1
	
	if (z_Bridges) {
    	def canPoll = false
    	z_Bridges.each { dev ->
        	def sN = dev.currentValue("serialNumber")
            if ("z_BridgesUsernameAPI_${sN}") canPoll = true        	
        }
        if (canPoll) poll1Minute()
    }

    def inputBridges= [
            name			: "z_Bridges",
            type			: "capability.bridge",
            title			: "Select Hue Bridges",
            multiple		: true,
            submitOnChange	: true,
            required		: false
        ] 
	
		dynamicPage(name: "pageBridges", title: "Bridges found by Super Lan Connect", uninstall: true, install:true) {
            section("Please select Hue Bridges that contain sensors") {
                input inputBridges          
            }
            if (z_Bridges) {
                z_Bridges.each { dev ->
                    def serialNumber = dev.currentValue("serialNumber")
                    def networkAddress = dev.currentValue("networkAddress")
                    def username = dev.currentValue("username") // HUE B Attribute
                    
                    section("Bridge ${dev}, Serial:${serialNumber}, IP:${networkAddress}, username for API is in device in IDE", hideable:true) {
                    	if (!username) {
                        	href(name: "${dev.id}", title: "IDE Bridge device",required: false, style: "external", url: "${getApiServerUrl()}/device/show/${dev.id}", description: "tap to view device in IDE")
                            input "z_BridgesUsernameAPI_${serialNumber}", "text", required:true, title:"Username for API", submitOnChange:true
                        }
                        else {
                        	paragraph username
                        	input "z_BridgesUsernameAPI_${serialNumber}", "text", required:true, title:"Username for API", submitOnChange:true, description:username
                        }
                    	
                        
                    }
                    
                }	
                if (state.devices) {
                    section("Associate a ST motion sensor with a Hue Sensor for monitoring during motion, sensor will be checked during motion") {
                    state.devices.each { item, sdev ->
                        input "z_motionSensor_${sdev.dni}", "capability.motionSensor", required:false, title:"Associated motion sensor ${sdev.name} "
                        }	
                    }
                }
                else section("No sensors found yet, wait a minute or so, or tap Done and reenter App")
			}
		}
}

def installed() {
	log.trace "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.trace "Updated with settings: ${settings}"
	unsubscribe()
	unschedule()
	initialize()
}

def uninstalled() {
	// Remove bridgedevice connection to allow uninstall of smartapp even though bridge is listed
	// as user of smartapp
    
	log.trace "Uninstall"
	unschedule()
    state.devices.each { key, dev -> deleteChildDevice(dev.dni)}
}

def initialize() {
	log.debug "Initializing"
	setupDeviceWatch()
    
    schedule("2015-01-09T12:00:00.000-0600", notifyNewVersion)
	
    if (settings.z_Bridges) {
    	subscribeToMotionEvents()
        runEvery1Minute("poll1Minute")
	}
}

def notifyNewVersion() {

	if (appVerInfo().split()[1] != runningVersion()) {
    	sendNotificationEvent("Hue Sensor App has a newer version, please visit IDE to update app/devices")
    }
}

def poll1Minute() {

    settings.z_Bridges.each { dev ->

		def serialNumber = dev.currentValue("serialNumber")
        def networkAddress = dev.currentValue("networkAddress")

		if (settings."z_BridgesUsernameAPI_${serialNumber}") {
        	poll(networkAddress, settings."z_BridgesUsernameAPI_${serialNumber}")
    		}
        
		}
}

def monitorSensor(evt) {
	// Looking for the last changed Hue Sensor button every 1s for 10 times
    // happens if you defined a normal motion sensor associated with a Hue sensor and motion has been detected.

	settings.each { key, value ->
    	if (value.toString() == evt.displayName.toString()) {

			// z_motionSensor_0AF130/sensor/2
            def sensor = key.split('/')[2].toString()
            def mac = key.split('_')[2].split("/")[0].toString()
            def usernameAPI = settings."z_BridgesUsernameAPI_${mac}"
			def dni = key.split('_')[2]

			settings.z_Bridges.each { dev ->
				def mac2 = dev.currentValue("serialNumber").toString()           	
                if (mac == mac2) {
            		def devSensor = getChildDevice(dni)
                	if (devSensor) {
                        def hostIP = dev.currentValue("networkAddress")
                        def i = 0

						for (i = 1; i <11; i++) {
                        	runIn(i, pollSensor, [data: [hostIP: hostIP, usernameAPI: usernameAPI, sensor: sensor], overwrite: false])
                        }
                	}
                }
            }
        }
    }   
}

def subscribeToMotionEvents() {

	state.devices.each { dni, sensor -> 
    	if (settings."z_motionSensor_${dni}") {
        	def motionSensor = settings."z_motionSensor_${dni}"
        	log.info "subscribe to motion for ${motionSensor} defined for ${sensor.name}"
            subscribe(motionSensor, "motion.active", monitorSensor)
        }
    }
}

private setupDeviceWatch() {
	def hub = location.hubs[0]
	// Make sure that all child devices are enrolled in device watch
	getChildDevices().each {
		it.sendEvent(name: "DeviceWatch-Enroll", value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${hub?.hub?.hardwareID}\"}")
	}
}

private updateSensorState(messageBody, mac) {
	def sensors = [:]
	// create sensor list of this bridge mac
    state.devices.each { key, sensor ->
    	if (sensor.mac == mac) {
        	sensors[sensor.item] = sensor
        }
    }
    
	// Copy of sensors used to locate old sensors in state that are no longer on bridge
	def toRemove = [:]
	toRemove << sensors

	messageBody.each { k, v ->

		if (v instanceof Map) {
			if (sensors[k] == null) {
				sensors[k] = [:]
			}
			toRemove.remove(k)
		}
	}

	// Remove sensors from state that are no longer discovered
	toRemove.each { k, v ->
    	
		log.warn "${sensors[k].name} no longer exists on bridge ${mac}, removing dni ${sensors[k].dni}"
        def dni = sensors[k].dni
        deleteChildDevice(dni)
		state.devices.remove(dni)
	}
}

def parse(childDevice, description) {
	log.warn "[Parse] entered ${childDevice} ${description}"
}

def handlePoll(physicalgraph.device.HubResponse hubResponse) {

    def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
  
    if (hubResponse.json.error) {
    	log.error "Error in ${mac} ${hubResponse.json.error}"	
        return
    }

	def body = hubResponse.json

    body.each { item, sensor ->

    	if (sensor.type == "ZLLLightLevel") {
        	if (sensor.state.lightlevel) {
            
            	// hue to lux => x = 10 ^ ((y - 1) / 10000)
                
                def float luxf = (10 ** ((sensor.state.lightlevel - 1) / 10000)).round(0)
                def lux = luxf.toInteger()
                def dni  = findStateDeviceWithUniqueId(getMac(sensor.uniqueid))
                if (state.devices[dni]) {
                    def sensorDev = getChildDevice(dni)
                    state.devices[dni].lightLevel = lux

                    if (!state.devices[dni].lightLevelLastupdated) {
                        state.devices[dni].lightLevelLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.lightEvent(lux)
                    }
                    else if (state.devices[dni].lightLevelLastupdated != sensor.state.lastupdated) {
                        state.devices[dni].lightLevelLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.lightEvent(lux)
                    }
				}
            }
		}
    	if (sensor.type == "ZLLTemperature") {
        	if (sensor.state.temperature) {
                def temp = (sensor.state.temperature / 100).toInteger()
                def dni  = findStateDeviceWithUniqueId(getMac(sensor.uniqueid))
                if (state.devices[dni]) {
                    def sensorDev = getChildDevice(dni)
                    state.devices[dni].temperature = temp

                    if (!state.devices[dni].temperatureLastupdated) {
                        state.devices[dni].temperatureLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.tempEvent(temp)
                    }
                    else if (state.devices[dni].temperatureLastupdated != sensor.state.lastupdated) {
                        state.devices[dni].temperatureLastupdated = sensor.state.lastupdated
                        if (sensorDev) sensorDev.tempEvent(temp)
                    }
            	}
			}
		}

    	if (sensor.type == "ZGPSwitch" || sensor.type == "ZLLPresence" || sensor.type == "ZLLSwitch" ) {
            def dni = mac + "/sensor/" + item
            def sensorDev = getChildDevice(dni)
            if (!sensorDev) {
            	def devType
                switch (sensor.type) {
                	case "ZGPSwitch":
                    	devType = "Hue Tap"
                        break
                	case "ZLLPresence":
                    	devType = "Hue Motion"
                        break
                	case "ZLLSwitch":
                    	devType = "Hue Switch"
                        break  
                }
            	log.info "Add Sensor ${dni} ${sensor.type} ${devType} ${sensor.name} ${getMac(sensor.uniqueid)}"
            	
                sensorDev = addChildDevice("verbem", devType, dni, null, [name:sensor.name, label:sensor.name, completedSetup:true])
                state.devices[dni] = [
                	'lastUpdated'	: sensor.state.lastupdated, 
                    'mac'			: mac, 
                    'item'			: item, 
                    'dni'			: dni,
                    'name'			: sensor.name,
                    'uniqueId'		: getMac(sensor.uniqueid),
                    'type'			: sensor.type,
                    'monitorTap'	: false,	
                    'id'			: sensorDev.id
                    ]
            }
                
            else 
            {
            	if (state.devices[dni].name != sensor.name) {
                	state.devices[dni].name = sensor.name
                    sensorDev.name = sensor.name
                    sensorDev.label = sensor.name
                }

				if (state.devices[dni].lastUpdated != sensor.state.lastupdated) {
                	state.devices[dni].lastUpdated = sensor.state.lastupdated
                    switch (state.devices[dni].type) {
                        case "ZGPSwitch":
                            log.info "Buttonpress Tap ${dni} ${sensor.state.buttonevent}"
                            sensorDev.buttonEvent(sensor.state.buttonevent)                          
                            break
                        case "ZLLPresence":
                            log.info "Motion Sensor ${dni} ${sensor.state.presence}"
                            sensorDev.motionEvent(sensor.state.presence)
                            sensorDev.batteryEvent(sensor.config.battery)
                        	break
                        case "ZLLSwitch":
                            log.info "Dimmer Switch ${dni} ${sensor.state.buttonevent}"
                            sensorDev.buttonEvent(sensor.state.buttonevent)
                            sensorDev.batteryEvent(sensor.config.battery)
                            break                    
	                }
                }
            }
        }
    }
        
    updateSensorState(body, mac)
}

def handlePollSensor(physicalgraph.device.HubResponse hubResponse) {

    def parsedEvent = parseEventMessage(hubResponse.description)
    def mac = parsedEvent.mac.substring(6)
    def body = hubResponse.json

    if (hubResponse.json.error) {
    	log.error "Error in ${mac} ${hubResponse.json.error}"	
        return
    }

	state.devices.each { key, sensor ->
        if (sensor.uniqueId == getMac(body.uniqueid)) {
            def dni = sensor.dni
            def sensorDev = getChildDevice(dni)
            if (!sensorDev) {
				log.error "Sensor ${dni} not found for update"
                }
            else {
                if (state.devices[dni].lastUpdated != body.state.lastupdated) {
                	state.devices[dni].lastUpdated = body.state.lastupdated
                    switch (state.devices[dni].type) {
                        case "ZGPSwitch":
                            log.info "Buttonpress Sensor ${dni} ${body.state.buttonevent}"
                            sensorDev.buttonEvent(body.state.buttonevent)                          
                            break
                        case "ZLLPresence":
                            log.info "Motion Sensor ${dni} ${body.state.presence}"
                            sensorDev.motionEvent(body.state.presence)                           
                        	break
                        case "ZLLSwitch":
                            log.info "Dimmer Switch ${dni} ${body.state.buttonevent}"
                            sensorDev.buttonEvent(body.state.buttonevent)
                            break                    
	                }
                }
            }
        }
    }
}

private poll(hostIP, usernameAPI) {

    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/${usernameAPI}/sensors/",
        headers: [HOST: "${hostIP}"],
        null,
        [callback: handlePoll] )

    sendHubCommand(hubAction)
}

def pollSensor(data) {

    def hubAction = new physicalgraph.device.HubAction(
        method: "GET",
        path: "/api/${data.usernameAPI}/sensors/${data.sensor}",
        headers: [HOST: "${data.hostIP}"],
        null,
        [callback: handlePollSensor] )

    sendHubCommand(hubAction)
}

private findStateDeviceWithUniqueId(uniqueId) {
	def stateDevice = state.devices.find {key, dev -> 
    	dev.uniqueId == uniqueId
    }
    return stateDevice.toString().split("=")[0]
}

private getMac(uniqueId) {
	def mac = uniqueId.split("-")[0]
	return mac
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex, 16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]), convertHexToInt(hex[2..3]), convertHexToInt(hex[4..5]), convertHexToInt(hex[6..7])].join(".")
}

private def parseEventMessage(String description) {
    def event = [:]
    def parts = description.split(',')
    parts.each { part ->
        part = part.trim()
        if (part.startsWith('devicetype:')) {
            def valueString = part.split(":")[1].trim()
            event.devicetype = valueString
        }
        else if (part.startsWith('mac:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.mac = valueString
            }
        }
        else if (part.startsWith('networkAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.ip = valueString
            }
        }
        else if (part.startsWith('deviceAddress:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.port = valueString
            }
        }
        else if (part.startsWith('ssdpPath:')) {
            def valueString = part.split(":")[1].trim()
            if (valueString) {
                event.ssdpPath = valueString
            }
        }
        else if (part.startsWith('ssdpUSN:')) {
            part -= "ssdpUSN:"
            def valueString = part.trim()
            if (valueString) {
                event.ssdpUSN = valueString
            }
        }
        else if (part.startsWith('ssdpTerm:')) {
            part -= "ssdpTerm:"
            def valueString = part.trim()
            if (valueString) {
                event.ssdpTerm = valueString
            }
        }
        else if (part.startsWith('headers')) {
            part -= "headers:"
            def valueString = part.trim()
            if (valueString) {
                event.headers = valueString
            }
        }
        else if (part.startsWith('body')) {
            part -= "body:"
            def valueString = part.trim()
            if (valueString) {
                event.body = valueString
            }
        }
    }

    event
}

def getWebData(params, desc, text=true) {
	try {
		httpGet(params) { resp ->
			if(resp.data) {
				if(text) {
					return resp?.data?.text.toString()
				} else { return resp?.data }
			}
		}
	}
	catch (ex) {
		if(ex instanceof groovyx.net.http.HttpResponseException) {
			log.error "${desc} file not found"
		} else {
			log.error "getWebData(params: $params, desc: $desc, text: $text) Exception:", ex
		}
		//sendExceptionData(ex, "getWebData")
		return "${label} info not found"
	}
}

private def appVerInfo()		{ return getWebData([uri: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/HueSensorData", contentType: "text/plain; charset=UTF-8"], "changelog") }