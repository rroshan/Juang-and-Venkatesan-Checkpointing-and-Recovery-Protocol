#!/bin/bash
#Author: Moses Ike. http://mosesike.org
#This script needs 2 argument. path to config file, and netid

PROG=MainDriver
PROGTEST=Tester
PROGBFS=BFS
testmc=dc45

#command line arguments
CONFIG=$1
netid=$2

#clear a custom debug file b4 each run/test
echo -e "" > debug.txt
#echo "after debug"

#Something the TA is making us do
config_file_name=$(echo $CONFIG | rev | cut -f1 -d"/" | rev | cut -f1 -d".") #without extension
#echo $config_file_name


# extract the important lines from the config file. the ones with no '#' or empty lines
sed -e "s/#.*//" $CONFIG | sed -e "/^\s*$/d" > temp
# insert a new line to EOF # necessary for the while loop
echo  >> temp

node_count=0
double_node_count=0
nodes_location="" #Stores a # delimited string of Location of each node
host_names=() #Stores the hostname of each node
neighbors_dict=() # Stores the Token path of each node
failure_events="" #Stores a # delimited string of Failure events of each node
port_ids=()
all_neighbors=""

current_line=1
# Reading from the temp file created above
while read line; 
do
	#turn all spaces to single line spaces
	line=$(echo $line | tr -s ' ')
########Extract Number of nodes and, min and max per Active
	if [ $current_line -eq 1 ]; then
		#number of nodes
		node_count=$(echo $line | cut -f1 -d" ")
		#convert it to an integer
  		let node_count=$node_count+0 
  		
  		#number of failures
  		failures=$(echo $line | cut -f2 -d" ")

  		#maxnumber of msgs
  		maxnumber=$(echo $line | cut -f3 -d" ")


  		#maxperactive neighbor set
  		maxperactive=$(echo $line | cut -f4 -d" ")

		#intermsgdelay 
  		intermsgdelay=$(echo $line | cut -f5 -d" ")

  		
  	else
#########Extract Location of each node
  		if [ $current_line -le $(expr $node_count + 1) ]; then
  			nodes_location+=$( echo -e $line"#" )	
  			node_id=$(echo $line | cut -f1 -d" ")
  			hostname=$(echo $line | cut -f2 -d" ")
  			host_names[$node_id]="$hostname"
			port_id=$(echo $line | cut -f3 -d " ")
			let port_id=$port_id+0
			port_ids[$node_id]=$port_id
  		else 
###########Extract Neighbors
			let double_node_count=$node_count+$node_count
			if [ $current_line -gt $(expr $node_count + 1) -a $current_line -le $(expr $double_node_count + 1) ]; then 	
				let node_id=$current_line-$node_count-2
  				neighbors=$(echo $line)
				all_neighbors+=$( echo -e $line"#" )
  				neighbors_dict+=(['"$node_id"']="$neighbors")
			else
 				failure_events+=$( echo -e $line"#" )
			fi
  		fi
  	fi
  	let current_line+=1
done < temp

# iterate through the date collected above and execute on the remote servers

config_name=$(echo $CONFIG | cut -f 1 -d '.')

max=0
for node_id in $(seq 0 $(expr $node_count - 1))
do
	let q=${port_ids[$node_id]}
	(($q > $max)) && max=$q
done

#echo "New Max Port"
#echo $max

((max=max+1))

#echo "Incremented Max Port"
#echo $max

maxport=65535

if ((max > maxport));
then
	((max=max%maxport))
	((max=max+20000))
fi

#echo "Tester Port after mod"
#echo $max


#echo "After mod"
#echo $max

found=0

#echo "Printing found value"
#echo $found

while [ $found -eq 0 ]
do
  	#echo "Inside While"
        found=1
        for node_id in $(seq 0 $(expr $node_count - 1))
        do
          	#echo "Inside for"
                #echo $max
                #echo ${port_ids[$node_id]}
                if ((max == port_ids[$node_id]));
                then
                    	#echo "Found match"
                        ((max=max+1))
                        found=0
                        #echo "Breaking"
                        break
                fi
        done
	if ((found = 0));
        then
            	continue
        else
            	#echo "Else not match found"
                break
        fi
done

#echo "final max"
#echo $max

#echo "all_neighbors"
#echo $all_neighbors

javac $PROGTEST.java
javac $PROG.java
javac $PROGBFS.java

java $PROGBFS "$all_neighbors" $config_file_name 

ssh -o StrictHostKeyChecking=no $netid@$testmc "cd $(pwd); java $PROGTEST $max $testmc $node_count $failures $config_file_name" &

for node_id in $(seq 0 $(expr $node_count - 1))
do
	host=${host_names[$node_id]}
	neighbors=${neighbors_dict["$node_id"]}
	#echo $netid@$host "java $PROG $node_id '$nodes_location' '$neighbors' '$minPerActive' '$maxPerActive' \
	#'$sendMinDelay' '$snapshotDelay' '$maxNumber' '$config_file_name' " &
	ssh -o StrictHostKeyChecking=no $netid@$host "cd $(pwd); java $PROG $node_id $node_count $failures $maxnumber $maxperactive $intermsgdelay '$nodes_location' '$neighbors' '$failure_events' $testmc $max $config_file_name" &
done

#sample output
#mji120030@dc45 java Project2 0 '0 dc45 19999#1 dc44 19998#2 dc43 19997#3 dc42 19996#4 dc41 19995#5 dc40 19898#' \
#'1 3' '6' '10' 	'100' '2000' '15' 'config' 

