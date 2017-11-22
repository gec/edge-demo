#!/bin/bash

PLAT_NAME=greenbus
PLAT_SCRIPT_NAME=greenbus

DIRNAME=`dirname "$0"`
echo $DIRNAME
PACKAGE_PATH=$DIRNAME

FULL_PATH=`pwd $DIRNAME`
echo "FULL PATH: $FULL_PATH"
echo "PACKAGE PATH: $PACKAGE_PATH"

USAGE="Usage: \n\tinstall_service.sh <local service name> <system service name>"

SERVICE_NAME=$1
SYSTEM_NAME=$2

if [ -z $SYSTEM_NAME ] || [ -z $SERVICE_NAME ] ; then 
	echo "$USAGE"
	exit 1
fi

if [ ! -f "$FULL_PATH/lib/service/$SERVICE_NAME/mainclass" ]; then
	echo "$SERVICE_NAME has no mainclass specification"
	exit 1
fi

if [ ! -f "$FULL_PATH/lib/service/$SERVICE_NAME/jarlist" ]; then
	echo "$SERVICE_NAME has no jarlist specification"
	exit 1
fi

SCRIPT_PATH="$FULL_PATH/bin/$PLAT_SCRIPT_NAME"
BASE_PATH="$FULL_PATH"
JAVA_CONFIG="-Dio.greenbus.config.base=$BASE_PATH/etc/"

cat install/init_alias_template.sh | sed 's]@@runscript@@]'$SCRIPT_PATH'];s/@@servname@@/'$SERVICE_NAME'/;s]@@kitebase@@]'$BASE_PATH'];s]@@javaconfig@@]'$JAVA_CONFIG']' > /etc/init.d/$SYSTEM_NAME
chmod +x /etc/init.d/$SYSTEM_NAME

update-rc.d $SYSTEM_NAME defaults 22
 

