///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000,2002,2003,2004,2005  Stuart Ballard <stuart.a.ballard@gmail.com>
// 
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package net.wuffies.japi;

class BoundCall implements CallWrapper, Comparable {
  private CallWrapper base;
  private Type returnType;
  private Type[] parameterTypes;
  private NonArrayRefType[] exceptionTypes;
  private TypeParam[] typeParams;
  private GenericWrapper containingWrapper;
  private boolean exclude14;
  private boolean exclude15;

  private BoundCall(CallWrapper base, GenericWrapper containingWrapper,
                    Type returnType, Type[] parameterTypes,
                    NonArrayRefType[] exceptionTypes, TypeParam[] typeParams,
                    boolean exclude14, boolean exclude15) {
    this.base = base;
    this.containingWrapper = containingWrapper;
    this.returnType = returnType;
    this.parameterTypes = parameterTypes;
    this.exceptionTypes = exceptionTypes;
    this.typeParams = typeParams;
    if (exclude14 && exclude15) throw new RuntimeException("Can't be excluded from *everywhere*!");
    this.exclude14 = exclude14;
    this.exclude15 = exclude15;
  }
  public BoundCall(CallWrapper base, GenericWrapper containingWrapper) {
    this(base, containingWrapper, base.getReturnType(), base.getParameterTypes(),
         base.getExceptionTypes(), base.getTypeParams(), false, false);
  }

  public Type getReturnType() {
    return returnType;
  }
  public Type[] getParameterTypes() {
    return parameterTypes;
  }
  public NonArrayRefType[] getExceptionTypes() {
    return exceptionTypes;
  }
  public TypeParam[] getTypeParams() {
    return typeParams;
  }
  public GenericWrapper getContainingWrapper() {
    return containingWrapper;
  }
  public boolean getExclude14() {
    return exclude14;
  }
  public boolean getExclude15() {
    return exclude15;
  }

  public BoundCall bind(ClassType t) {
    if (exclude15) return this;

    Type[] newParameterTypes = new Type[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      newParameterTypes[i] = parameterTypes[i].bindWithFallback(t);
    }
    NonArrayRefType[] newExceptionTypes = new NonArrayRefType[exceptionTypes.length];
    for (int i = 0; i < exceptionTypes.length; i++) {
      newExceptionTypes[i] = (NonArrayRefType) exceptionTypes[i].bindWithFallback(t);
    }
    TypeParam[] newTypeParams = null;
    if (typeParams != null) {
      newTypeParams = new TypeParam[typeParams.length];
      for (int i = 0; i < typeParams.length; i++) {
        newTypeParams[i] = (TypeParam) typeParams[i].bindWithFallback(t);
      }
    }
    BoundCall result = new BoundCall(base, containingWrapper, returnType.bindWithFallback(t),
                                    newParameterTypes, newExceptionTypes, newTypeParams,
                                    exclude14, exclude15);
    if (!getNonGenericSig().equals(result.getNonGenericSig())) {
      // shouldn't happen - exclude15 was handled above...
      if (result.exclude15) throw new RuntimeException("Can't be excluded from *everywhere*!");
      result.exclude14 = true;
    }
    return result;
  }
  public BoundCall bind14() {
    if (exclude14) return null;

    Type[] newParameterTypes = new Type[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      newParameterTypes[i] = parameterTypes[i].getNonGenericType();
    }
    NonArrayRefType[] newExceptionTypes = new NonArrayRefType[exceptionTypes.length];
    for (int i = 0; i < exceptionTypes.length; i++) {
      newExceptionTypes[i] = (NonArrayRefType) exceptionTypes[i].getNonGenericType();
    }
    return new BoundCall(base, containingWrapper, returnType.getNonGenericType(),
                         newParameterTypes, newExceptionTypes, null, exclude14, true);
  }

  public int getModifiers() {return base.getModifiers();}
  public boolean isDeprecated() {return base.isDeprecated();}
  public String getName() {return base.getName();}
  public Object getDefaultValue() {return base.getDefaultValue();}
  public ClassWrapper getDeclaringClass() {return base.getDeclaringClass();}
  public boolean isInheritable() {return base.isInheritable();}

  private static String getNonGenericSig(CallWrapper call) {
		String sig = call.getName() + "(";
		Type[] parameterTypes = call.getParameterTypes();
		for (int i = 0; i < parameterTypes.length; i++) 
		{
        if (i > 0) sig += ",";
		    sig += parameterTypes[i].getNonGenericTypeSig();
		}
		sig += ")";
    return sig;
  }
  public String getNonGenericSig() {
    return getNonGenericSig(this);
  }

  public int compareTo(CallWrapper call) {
    return getNonGenericSig().compareTo(getNonGenericSig(call));
  }
  public int compareTo(Object o) {
    return compareTo((CallWrapper) o);
  }

  public String toString() {
    return containingWrapper + "." + getNonGenericSig();
  }
}