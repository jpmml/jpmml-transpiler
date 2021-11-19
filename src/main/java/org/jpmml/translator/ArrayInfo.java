/*
 * Copyright (c) 2021 Villu Ruusmann
 *
 * This file is part of JPMML-Transpiler
 *
 * JPMML-Transpiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Transpiler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Transpiler.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.translator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.OpType;

public class ArrayInfo {

	private String name = null;

	private BiMap<Integer, DataField> dataFields = HashBiMap.create();


	public ArrayInfo(String name){
		setName(name);
	}

	public int getIndex(DataField dataField){
		BiMap<Integer, DataField> dataFields = getDataFields();

		return (dataFields.inverse()).get(dataField);
	}

	public List<Integer> getIndices(){
		Map<Integer, DataField> dataFields = getDataFields();

		return (dataFields.keySet()).stream()
			.sorted()
			.collect(Collectors.toList());
	}

	public DataType getDataType(){
		Map<Integer, DataField> dataFields = getDataFields();

		Set<DataType> dataTypes = (dataFields.values()).stream()
			.map(dataField -> dataField.getDataType())
			.collect(Collectors.toSet());

		return Iterables.getOnlyElement(dataTypes);
	}

	public OpType getOpType(){
		Map<Integer, DataField> dataFields = getDataFields();

		Set<OpType> opTypes = (dataFields.values()).stream()
			.map(dataField -> dataField.getOpType())
			.collect(Collectors.toSet());

		return Iterables.getOnlyElement(opTypes);
	}

	public DataField getElement(int index){
		return this.dataFields.get(index);
	}

	public void setElement(int index, DataField dataField){

		if(dataField.hasExtensions()){
			throw new IllegalArgumentException();
		} // End if

		if(dataField.hasIntervals() || dataField.hasValues()){
			throw new IllegalArgumentException();
		}

		this.dataFields.put(index, dataField);
	}

	public String getName(){
		return this.name;
	}

	private void setName(String name){
		this.name = Objects.requireNonNull(name);
	}

	public BiMap<Integer, DataField> getDataFields(){
		return this.dataFields;
	}
}