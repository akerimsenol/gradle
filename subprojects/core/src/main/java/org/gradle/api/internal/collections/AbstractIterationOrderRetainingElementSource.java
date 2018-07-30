/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.collections;

import com.google.common.base.Objects;
import com.google.common.collect.Iterators;
import org.gradle.api.Action;
import org.gradle.api.internal.provider.ProviderInternal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

abstract public class AbstractIterationOrderRetainingElementSource<T> implements ElementSource<T> {
    // This set represents the order in which elements are inserted to the store, either actual
    // or provided.  We construct a correct iteration order from this set.
    private final List<Element<T>> inserted = new ArrayList<Element<T>>();

    private Action<T> realizeAction;

    List<Element<T>> getInserted() {
        return inserted;
    }

    @Override
    public boolean isEmpty() {
        return inserted.isEmpty();
    }

    @Override
    public boolean constantTimeIsEmpty() {
        return inserted.isEmpty();
    }

    @Override
    public int size() {
        return inserted.size();
    }

    @Override
    public int estimatedSize() {
        return inserted.size();
    }

    @Override
    public boolean contains(Object element) {
        return Iterators.contains(iterator(), element);
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
        for (Object e : elements) {
            if (!contains(e)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addRealized(T element) {
        return true;
    }

    @Override
    public boolean remove(Object o) {
        Iterator<Element<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            Element<? extends T> provider = iterator.next();
            if (provider.isRealized() && provider.getValue().equals(o)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        inserted.clear();
    }

    @Override
    public void realizeExternal(ProviderInternal<? extends T> provider) {

    }

    @Override
    public void realizePending() {
        for (Element<T> provider : inserted) {
            if (!provider.isRealized()) {
                provider.realize();
            }
        }
    }

    @Override
    public void realizePending(Class<?> type) {
        for (Element<T> provider : inserted) {
            if (!provider.isRealized() && (provider.getType() == null || type.isAssignableFrom(provider.getType()))) {
                provider.realize();
            }
        }
    }

    Element<T> cachingElement(ProviderInternal<? extends T> provider) {
        return new CachingElement<T>(provider, realizeAction);
    }

    @Override
    public boolean removePending(ProviderInternal<? extends T> provider) {
        Iterator<Element<T>> iterator = inserted.iterator();
        while (iterator.hasNext()) {
            Element<T> next = iterator.next();
            if (next.caches(provider)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRealize(final Action<T> action) {
        this.realizeAction = action;
    }

    protected interface Element<T> {
        boolean isRealized();
        boolean caches(ProviderInternal<? extends T> provider);
        void realize();
        Class<? extends T> getType();
        T getValue();
        ProviderInternal<? extends T> getDelegate();
    }

    // TODO Check for comodification with the ElementSource
    protected static abstract class AbstractElementCollectionIterator<T, I> implements Iterator<I> {
        final List<Element<T>> backingList;
        private final Collection<I> values;
        int nextIndex = -1;
        int previousIndex = -1;
        Element<T> next;

        AbstractElementCollectionIterator(List<Element<T>> backingList, Collection<I> values) {
            this.backingList = backingList;
            this.values = values;
            updateNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        protected abstract I valueOf(Element<T> element);

        protected abstract boolean isValidCandidate(Element<T> element);

        private void updateNext() {
            int i = nextIndex + 1;
            while (i < backingList.size()) {
                Element<T> candidate = backingList.get(i);
                if (isValidCandidate(candidate)) {
                    I value = valueOf(candidate);
                    if (values.add(value)) {
                        nextIndex = i;
                        next = candidate;
                        return;
                    }
                }
                i++;
            }
            nextIndex = i;
            next = null;
        }

        @Override
        public I next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            I thisNext = valueOf(next);
            previousIndex = nextIndex;
            updateNext();
            return thisNext;
        }

        @Override
        public void remove() {
            if (previousIndex > -1) {
                I value = valueOf(backingList.get(previousIndex));
                backingList.remove(previousIndex);
                values.remove(value);
                previousIndex = -1;
                nextIndex--;
            } else {
                throw new IllegalStateException();
            }
        }
    }

    protected static class RealizedElementCollectionIterator<T> extends AbstractElementCollectionIterator<T, T> {
        RealizedElementCollectionIterator(List<Element<T>> backingList, Collection<T> values) {
            super(backingList, values);
        }

        @Override
        protected T valueOf(Element<T> element) {
            return element.getValue();
        }

        @Override
        protected boolean isValidCandidate(Element<T> element) {
            return element.isRealized();
        }
    }

    protected static class PendingElementCollectionIterator<T> extends AbstractElementCollectionIterator<T, ProviderInternal<? extends T>> {
        PendingElementCollectionIterator(List<Element<T>> backingList, Collection<ProviderInternal<? extends T>> values) {
            super(backingList, values);
        }

        @Override
        protected ProviderInternal<? extends T> valueOf(Element<T> element) {
            return element.getDelegate();
        }

        @Override
        protected boolean isValidCandidate(Element<T> element) {
            return !element.isRealized();
        }
    }

    protected static class CachingElement<T> implements Element<T> {
        private final ProviderInternal<? extends T> delegate;
        private T value;
        private boolean realized;
        private final Action<T> realizeAction;

        CachingElement(final ProviderInternal<? extends T> delegate, Action<T> realizeAction) {
            this.delegate = delegate;
            this.realizeAction = realizeAction;
        }

        CachingElement(T value) {
            this.value = value;
            this.realized = true;
            this.realizeAction = null;
            this.delegate = null;
        }

        @Override
        public ProviderInternal<? extends T> getDelegate() {
            return delegate;
        }

        @Override
        public Class<? extends T> getType() {
            if (delegate != null) {
                return delegate.getType();
            } else {
                return null;
            }
        }

        @Override
        public boolean isRealized() {
            return realized;
        }

        @Override
        public void realize() {
            if (value == null && delegate != null) {
                value = delegate.get();
                realized = true;
                if (realizeAction != null) {
                    realizeAction.execute(value);
                }
            }
        }

        @Nullable
        @Override
        public T getValue() {
            if (!realized) {
                realize();
            }
            return value;
        }

        @Override
        public boolean caches(ProviderInternal<? extends T> provider) {
            return Objects.equal(delegate, provider);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            IterationOrderRetainingSetElementSource.CachingElement that = (IterationOrderRetainingSetElementSource.CachingElement) o;
            return Objects.equal(delegate, that.delegate) &&
                Objects.equal(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(delegate, value);
        }
    }
}
