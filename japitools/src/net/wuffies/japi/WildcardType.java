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

public class WildcardType extends RefType {

  private NonArrayRefType upperBound;
  private NonArrayRefType lowerBound;

  public WildcardType() {
    this(null);
  }
  public WildcardType(NonArrayRefType upperBound) {
    this(upperBound, null);
  }
  public WildcardType(NonArrayRefType upperBound, NonArrayRefType lowerBound) {
    if (upperBound == null) upperBound = new ClassType("java.lang.Object");
    if (!(upperBound instanceof ClassType && "java.lang.Object".equals(((ClassType)upperBound).getName()))) {
      if (lowerBound != null) throw new RuntimeException("Upper and lower bounds cannot both be set");
    }

    this.upperBound = upperBound;
    this.lowerBound = lowerBound;
  }

  public NonArrayRefType getUpperBound() {
    return upperBound;
  }
  public NonArrayRefType getLowerBound() {
    return lowerBound;
  }

  public String getTypeSig(GenericWrapper wrapper) {
    if (lowerBound == null) {
      return "{" + upperBound.getTypeSig(wrapper);
    } else {
      return "}" + lowerBound.getTypeSig(wrapper);
    }
  }
  public String getNonGenericTypeSig() {
    return upperBound.getNonGenericTypeSig();
  }
  public Type getNonGenericType() {
    return upperBound.getNonGenericType();
  }
  public void resolveTypeParameters() {
    upperBound = (NonArrayRefType) resolveTypeParameter(upperBound);
    lowerBound = (NonArrayRefType) resolveTypeParameter(lowerBound);
  }
  public Type bind(ClassType t) {
    debugStart("Bind", "to " + t);
    try {
      return new WildcardType((NonArrayRefType) upperBound.bindWithFallback(t),
                              lowerBound == null ? null : (NonArrayRefType) lowerBound.bind(t));
    } finally {
      debugEnd();
    }
  }
  public String toStringImpl() {
    return "Wildcard:" + upperBound + "/" + lowerBound;
  }
}
